package bot.wuliang.command

import bot.wuliang.adapter.context.ExecutionContext
import bot.wuliang.config.WfMarketConfig.WF_LICHORDER_KEY
import bot.wuliang.distribute.annotation.AParameter
import bot.wuliang.distribute.annotation.ActionService
import bot.wuliang.distribute.annotation.Executor
import bot.wuliang.entity.vo.WfMarketVo
import bot.wuliang.imageProcess.WebImgUtil
import bot.wuliang.logAop.SystemLog
import bot.wuliang.market.MarketAuctionSource
import bot.wuliang.market.MarketItemLookupService
import bot.wuliang.redis.RedisService
import bot.wuliang.respEnum.WarframeRespEnum
import bot.wuliang.riven.*
import bot.wuliang.service.WfAliasService
import bot.wuliang.service.WfRivenService
import bot.wuliang.translation.PublicExportService
import bot.wuliang.utils.PagedCommand
import bot.wuliang.utils.paginate
import org.springframework.stereotype.Component
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.regex.Matcher

/** 紫卡、玄骸与姐妹拍卖指令，共用市场请求与查询结果存储。 */
@Component
@ActionService
class AuctionCommands(
    private val marketItemLookupService: MarketItemLookupService,
    private val marketAuctionSource: MarketAuctionSource,
    private val translations: PublicExportService,
    private val webImgUtil: WebImgUtil,
    private val wfAliasService: WfAliasService,
    private val wfRivenService: WfRivenService,
    private val redisService: RedisService,
    private val rivenQueryCriteriaResolver: RivenQueryCriteriaResolver,
    private val rivenAuctionDecoder: RivenAuctionDecoder,
    private val rivenAuctionResultStore: RivenAuctionResultStore,
) {
    companion object {
        private const val KUVA_WEAPON_MARKER = "kuva"
    }

    /** 解析紫卡指令中的武器、属性和页码条件，查询拍卖并发布结果图片或未命中提示。 */
    @SystemLog(businessName = "获取WM市场紫卡信息")
    @AParameter
    @Executor(action = "(?i)\\b(wr|wmr)\\s*(\\S+.*)$")
    suspend fun getRiven(context: ExecutionContext, matcher: Matcher) {
        if (!translations.ready) {
            context.sender.sendText(PublicExportService.UNAVAILABLE_MESSAGE)
            return
        }
        val generation = translations.cacheKey(bot.wuliang.config.WfMarketConfig.WF_RIVEN_RESULT_KEY_PREFIX)
        val pagedCommand = PagedCommand.parse(matcher.group(2))
        val key = pagedCommand.content
        val parameterList = key.trim().split(Regex("\\s+"))

        // 正则匹配紫卡循环次数
        val pattern = """(?<=\D)\d+(?=洗)""".toRegex()
        val matchResult = pattern.find(key)
        val reRollTimes = matchResult?.value?.toInt()

        val beforeItemNameKey: String = parameterList.first()
        val itemNameKey = beforeItemNameKey.replace(Regex("信条·|赤毒·|信条|赤毒"), "")
        val itemEntity = wfRivenService.turnKeyToUrlNameByLich(itemNameKey)
            ?: wfRivenService.searchByRivenLike(itemNameKey).firstOrNull()
            ?: run {
                marketItemLookupService.handleFuzzySearch(context, itemNameKey)
                return
            }

        val criteria = when (
            val resolution = rivenQueryCriteriaResolver.resolve(itemEntity.urlName!!, parameterList.drop(1))
        ) {
            is RivenCriteriaResolution.Success -> resolution.criteria
            is RivenCriteriaResolution.UnknownAttribute -> {
                val suggestions = resolution.suggestions
                    .joinToString("、") { it.zhName.ifBlank { it.enName } }
                    .ifBlank { "暂无相近词条" }
                context.sender.sendText("无法识别紫卡词条，可能的紫卡词条：$suggestions")
                return
            }

            is RivenCriteriaResolution.AmbiguousAttribute -> {
                val candidates = resolution.candidates.joinToString("、") { it.zhName.ifBlank { it.enName } }
                context.sender.sendText("紫卡词条存在歧义，请输入更完整的名称：$candidates")
                return
            }

            is RivenCriteriaResolution.ConflictingNegativeAttributes -> {
                context.sender.sendText("每次查询只能指定一个负词条条件")
                return
            }
        }

        val rivenJson = marketAuctionSource.getRivenAuctionsJson(criteria)

        if (rivenJson == null) {
            context.sender.sendText(WarframeRespEnum.SEARCH_ERROR.message)
            return
        }

        // 筛选和格式化拍卖数据
        val orderList = when (
            val decoded = rivenAuctionDecoder.decode(rivenJson, itemEntity.zhName!!, reRollTimes, pagedCommand.page)
        ) {
            RivenAuctionDecodeResult.Empty -> {
                context.sender.sendText(WarframeRespEnum.SEARCH_RIVEN_NOT_FOUND.message + itemEntity.zhName)
                return
            }

            is RivenAuctionDecodeResult.Success -> decoded.value
        }
        val resultId = rivenAuctionResultStore.publish(orderList, generation)

        val imgData = WebImgUtil.ImgData(
            url = "http://${webImgUtil.frontendAddress}/riven?resultId=$resultId",
            imgName = "riven-${UUID.randomUUID()}",
            element = "#app",
            waitElement = ".warframeRiven"
        )

        val url = webImgUtil.getImgUrl(imgData)
        context.sender.sendImage(url)
        webImgUtil.deleteImg(imgData = imgData)

    }

    /** 解析玄骸或姐妹武器指令中的元素、幻纹及分页条件，缓存查询结果后发送图片。 */
    @SystemLog(businessName = "获取WM市场玄骸武器信息")
    @AParameter
    @Executor(action = "(?i)\\bwl\\s*(\\S+.*)$")
    suspend fun getLich(context: ExecutionContext, matcher: Matcher) {
        if (!translations.ready) {
            context.sender.sendText(PublicExportService.UNAVAILABLE_MESSAGE)
            return
        }
        val generation = translations.cacheKey(WF_LICHORDER_KEY)
        val pagedCommand = PagedCommand.parse(matcher.group(1))
        val key = pagedCommand.content
        val parameterList = key.split(" ")

        val regex = """\d+""".toRegex()
        val matchResult = regex.find(key)
        val damage = matchResult?.value?.toInt()

        val beforeItemNameKey: String = parameterList.first()
        val itemNameKey = beforeItemNameKey.replace(Regex("信条·|赤毒·|信条|赤毒"), "")
        val itemEntity = wfRivenService.turnKeyToUrlNameByLich(itemNameKey)
            ?: wfRivenService.turnKeyToUrlNameByLichLike(itemNameKey).firstOrNull()
            ?: run {
                // 如果没有找到匹配项
                marketItemLookupService.handleFuzzySearch(context, itemNameKey)
                return
            }

        val otherParams = parameterList.drop(1)
        val element: String? = otherParams.firstOrNull { !it.matches(Regex("([有无])")) }
        val ephemera: String? = otherParams.firstOrNull { it.contains("无") || it.contains("有") }

        val urlElement: String? = element?.let { wfAliasService.getOtherName(it) }
        val lichType = if (itemEntity.urlName!!.contains(KUVA_WEAPON_MARKER)) RivenGroups.LICH else RivenGroups.SISTER

        val lichCacheKey =
            "${generation}:${itemEntity.urlName}:${damage}:${element}:${ephemera}:page=${pagedCommand.page}"
        if (!redisService.hasKey(lichCacheKey)) {
            val lichJson = marketAuctionSource.getLichAuctionsJson(
                element = urlElement,
                ephemera = ephemera,
                itemEntityUrlName = itemEntity.urlName,
                lichType = lichType
            )

            if (lichJson == null) {
                context.sender.sendText(WarframeRespEnum.SEARCH_ERROR.message)
                return
            }

            // 筛选和格式化拍卖数据
            val orders = lichJson["payload"]["auctions"]

            val matchingOrders = orders.asSequence()
                .filter { if (damage != null) it["item"]["damage"].intValue() == damage else true }
                .toList()
            val orderPage = matchingOrders.paginate(pagedCommand.page, MarketDefaults.AUCTION_PAGE_SIZE)
            val orderInfos = orderPage.items.map { order ->
                WfMarketVo.LichOrderInfo(
                    element = translations.name(order["item"]["element"].textValue()),
                    havingEphemera = order["item"]["having_ephemera"].booleanValue(),
                    damage = order["item"]["damage"].intValue(),
                    startPlatinum = order["starting_price"]?.intValue() ?: order["buyout_price"].intValue(),
                    buyOutPlatinum = order["buyout_price"]?.intValue() ?: order["starting_price"].intValue(),
                )
            }

            val lichOrderEntity = WfMarketVo.LichEntity(
                lichName = itemEntity.zhName!!,
                lichOrderInfoList = orderInfos,
                currentPage = orderPage.currentPage,
                totalPages = orderPage.totalPages,
            )

            redisService.setValueWithExpiry(
                lichCacheKey,
                lichOrderEntity,
                MarketDefaults.LICH_CACHE_TTL_SECONDS,
                TimeUnit.SECONDS
            )
        }

        val imgData = WebImgUtil.ImgData(
            url = "http://${webImgUtil.frontendAddress}/lich?url_name=${itemEntity.urlName}&damage=${damage}" +
                    "&element=${element}&ephemera=${ephemera}&page=${pagedCommand.page}",
            imgName = "lich-${UUID.randomUUID()}",
            element = "#app",
            waitElement = ".warframeLich"
        )

        val url = webImgUtil.getImgUrl(imgData)
        context.sender.sendImage(url)
        webImgUtil.deleteImg(imgData = imgData)
    }
}
