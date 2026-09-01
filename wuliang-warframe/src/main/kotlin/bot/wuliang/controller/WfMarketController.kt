package bot.wuliang.controller

import bot.wuliang.adapter.context.ExecutionContext
import bot.wuliang.aipOcr.AipOcrClient
import bot.wuliang.config.WARFRAME_AMP_PNG
import bot.wuliang.config.WARFRAME_CETUS_WISP_PNG
import bot.wuliang.config.WfMarketConfig.WF_LICHORDER_KEY
import bot.wuliang.distribute.annotation.AParameter
import bot.wuliang.distribute.annotation.ActionService
import bot.wuliang.distribute.annotation.Executor
import bot.wuliang.entity.WfMarketItemEntity
import bot.wuliang.entity.vo.WfMarketVo
import bot.wuliang.httpUtil.HttpUtil.urlEncode
import bot.wuliang.imageProcess.WebImgUtil
import bot.wuliang.jacksonUtil.JacksonUtil
import bot.wuliang.logAop.SystemLog
import bot.wuliang.message.BotMessage
import bot.wuliang.moudles.WmDucats
import bot.wuliang.otherUtil.OtherUtil
import bot.wuliang.redis.RedisService
import bot.wuliang.respEnum.WarframeRespEnum
import bot.wuliang.riven.*
import bot.wuliang.service.WfLexiconService
import bot.wuliang.service.WfMarketItemService
import bot.wuliang.service.WfRivenService
import bot.wuliang.utils.PagedCommand
import bot.wuliang.utils.ParseDataUtil
import bot.wuliang.utils.WfUtil
import bot.wuliang.utils.paginate
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.regex.Matcher


/**
 * @description: Warframe 市场
 * @author Nature Zero
 * @date 2024/5/20 上午9:03
 */
@Component
@ActionService
class WfMarketController(
    private val wfUtil: WfUtil,
    private val webImgUtil: WebImgUtil,
    private val wfLexiconService: WfLexiconService,
    private val wfRivenService: WfRivenService,
    @Qualifier("otherUtil") private val otherUtil: OtherUtil,
    private val redisService: RedisService,
    private val aipOcrClient: AipOcrClient,
    private val parseDataUtil: ParseDataUtil,
    private val rivenQueryCriteriaResolver: RivenQueryCriteriaResolver,
    private val rivenAuctionDecoder: RivenAuctionDecoder,
    private val rivenAuctionResultStore: RivenAuctionResultStore,
    private val wfMarketItemService: WfMarketItemService,
) {
    private val wmRateLimiter = kotlinx.coroutines.sync.Semaphore(2)

    companion object {
        private const val KUVA_WEAPON_MARKER = "kuva"
    }

    @SystemLog(businessName = "获取WM市场物品信息")
    @AParameter
    @Executor(action = "(?i)\\bwm\\s*(\\S+.*)$")
    suspend fun getMarketItem(context: ExecutionContext, matcher: Matcher) {
        val pagedCommand = PagedCommand.parse(matcher.group(1))
        val page = pagedCommand.requestedPage
        val key = pagedCommand.content
        val regex = """(\d+)(?=级)|(满级)""".toRegex()
        val matchResult = regex.find(key)
        val level = matchResult?.value

        // 移除匹配到的部分并去除多余的空格
        val cleanKey = matchResult?.let { key.replace("${it.value}级", "").replace("满级", "").trim() } ?: key
        val redisKey = "warframe:lexicon:$cleanKey"

        // 尝试从Redis获取数据
        val lexiconEntity = redisService.getValueTyped<WfMarketItemEntity>(redisKey)
        if (lexiconEntity !=
            null
        ) {
            wfUtil.sendMarketItemInfo(context, lexiconEntity, level, page)
            return
        }

        // Redis中没有数据，从数据库中查询
        val itemEntity = wfUtil.fetchItemEntity(cleanKey)
            ?: run {
                // 模糊查询
                val fuzzyList = cleanKey
                    .asSequence()
                    .map { it.toString() }
                    .filter {
                        val c = it[0]
                        when {
                            c in 'a'..'z' -> false
                            c in 'A'..'Z' -> false
                            c.isDigit() -> false
                            else -> true
                        }
                    }
                    .flatMap {
                        wfMarketItemService.fuzzyQuery(it).asSequence().mapNotNull { item -> item?.zhName }
                    }
                    .distinct()
                    .toList()
                if (fuzzyList.isNotEmpty()) {
                    otherUtil.findMatchingStrings(cleanKey, fuzzyList).let {
                        context.sender.sendText(WarframeRespEnum.SEARCH_NOT_FOUND.message + it.joinToString(", "))
                    }
                } else context.sender.sendText(WarframeRespEnum.SEARCH_MATCH_NOT_FOUND.message)
                return
            }
        wfUtil.sendMarketItemInfo(context, itemEntity, level, page)
        return
    }

    @SystemLog(businessName = "获取WM市场紫卡信息")
    @AParameter
    @Executor(action = "(?i)\\b(wr|wmr)\\s*(\\S+.*)$")
    suspend fun getRiven(context: ExecutionContext, matcher: Matcher) {
        val pagedCommand = PagedCommand.parse(matcher.group(2))
        val key = pagedCommand.content
        val parameterList = key.split(" ")

        // 正则匹配紫卡循环次数
        val pattern = """(?<=\D)\d+(?=洗)""".toRegex()
        val matchResult = pattern.find(key)
        val reRollTimes = matchResult?.value?.toInt()

        val beforeItemNameKey: String = parameterList.first()
        val itemNameKey = beforeItemNameKey.replace(Regex("信条·|赤毒·|信条|赤毒"), "")
        val itemEntity = wfRivenService.turnKeyToUrlNameByLich(itemNameKey)
            ?: wfRivenService.searchByRivenLike(itemNameKey).firstOrNull()
            ?: run {
                wfUtil.handleFuzzySearch(context, itemNameKey)
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
                context.sender.sendText("紫卡词条存在歧义，可能的紫卡词条：$candidates")
                return
            }

            is RivenCriteriaResolution.ConflictingNegativeAttributes -> {
                context.sender.sendText("每次查询只能指定一个负词条条件")
                return
            }
        }

        val rivenJson = wfUtil.getRivenAuctionsJson(criteria)

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
        val resultId = rivenAuctionResultStore.publish(orderList)

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

    @SystemLog(businessName = "获取WM市场玄骸武器信息")
    @AParameter
    @Executor(action = "(?i)\\bwl\\s*(\\S+.*)$")
    suspend fun getLich(context: ExecutionContext, matcher: Matcher) {
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
                wfUtil.handleFuzzySearch(context, itemNameKey)
                return
            }

        val otherParams = parameterList.drop(1)
        val element: String? = otherParams.firstOrNull { !it.matches(Regex("([有无])")) }
        val ephemera: String? = otherParams.firstOrNull { it.contains("无") || it.contains("有") }

        val urlElement: String? = element?.let { wfLexiconService.getOtherName(it) }
        val lichType = if (itemEntity.urlName!!.contains(KUVA_WEAPON_MARKER)) RivenGroups.LICH else RivenGroups.SISTER

        val lichCacheKey =
            "${WF_LICHORDER_KEY}:${itemEntity.urlName}:${damage}:${element}:${ephemera}:page=${pagedCommand.page}"
        if (!redisService.hasKey(lichCacheKey)) {
            val lichJson = wfUtil.getLichAuctionsJson(
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
                    element = wfLexiconService.getOtherEnName(order["item"]["element"].textValue())!!,
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

    @SystemLog(businessName = "获取物品Wiki链接")
    @AParameter
    @Executor(action = "wiki (.*)")
    suspend fun getWikiUrl(context: ExecutionContext, matcher: Matcher) {
        val key = matcher.group(1)
        val wikiUrl = "https://warframe.huijiwiki.com/wiki/${key.urlEncode()}"
        context.sender.sendText(WarframeRespEnum.SEARCH_WIKI.message + wikiUrl)
    }

    @SystemLog(businessName = "获取增幅器序号")
    @AParameter
    @Executor(action = "(?i)\\b(增幅器|指挥官|指挥官武器|amp)\\b")
    suspend fun getAmp(context: ExecutionContext) {
        val imgData = WebImgUtil.ImgData(
            url = WARFRAME_AMP_PNG,
            imgName = "amp",
            local = true,
        )
        val url = webImgUtil.getImgUrl(imgData = imgData)
        context.sender.sendImage(url)
    }

    @SystemLog(businessName = "获取希图斯幽魂")
    @AParameter
    @Executor(action = "(?i)\\b(幽魂|希图斯幽魂)\\b")
    suspend fun getCetusWisp(context: ExecutionContext) {
        val imgData = WebImgUtil.ImgData(
            url = WARFRAME_CETUS_WISP_PNG,
            imgName = "cetus_wisp",
            local = true,
        )
        val url = webImgUtil.getImgUrl(imgData = imgData)
        context.sender.sendImage(url)
    }

    @SystemLog(businessName = "获取物品对应翻译")
    @AParameter
    @Executor(action = "(?i)\\b翻译 (.*)\\b")
    suspend fun translation(context: ExecutionContext, matcher: Matcher) {
        val inputText = matcher.group(1).trim()

        // 判断输入语言类型
        val hasChinese = Regex("[\\u4e00-\\u9fa5]").containsMatchIn(inputText)
        val hasEnglish = Regex("[A-Za-z]").containsMatchIn(inputText)

        // 封装查找和模糊搜索逻辑
        suspend fun findTranslation(
            query: String,
            directLookup: (String) -> String?,
            fuzzyLookup: (String) -> List<String?>
        ): Boolean {
            val directResult = directLookup(query)
            if (directResult != null) {
                context.sender.sendText(directResult)
                return true
            }

            val fuzzyResults = fuzzyLookup(query)
                .filterNotNull()
                .takeIf { it.isNotEmpty() }
                ?.let { otherUtil.findMatchingStrings(query, it) }

            if (!fuzzyResults.isNullOrEmpty()) {
                context.sender.sendText("${WarframeRespEnum.SEARCH_NOT_FOUND.message}${fuzzyResults.joinToString(", ")}")
                return true
            }

            return false
        }

        // 根据输入语言执行翻译逻辑
        when {
            hasChinese && hasEnglish -> {
                if (!findTranslation(inputText, wfLexiconService::getEnName) { key ->
                        wfLexiconService.fuzzyQuery(key).map { it?.zhItemName }
                    }) {
                    context.sender.sendText(WarframeRespEnum.SEARCH_MATCH_NOT_FOUND.message)
                    return
                }
            }

            hasEnglish -> {
                if (!findTranslation(inputText, wfLexiconService::getZhName) { key ->
                        wfLexiconService.fuzzyQuery(key).map { it?.enItemName }
                    }) {
                    context.sender.sendText(WarframeRespEnum.SEARCH_MATCH_NOT_FOUND.message)
                    return
                }
            }

            else -> context.sender.sendText(WarframeRespEnum.SEARCH_MATCH_NOT_FOUND.message)
        }
    }

    @SystemLog(businessName = "获取部件在WM的白金价格")
    @AParameter
    @Executor(action = "(?i)\\b(部件|WM价格|WM价格查询)\\b")
    suspend fun getWmPrice(context: ExecutionContext) {
        val imageMessages = context.messages.filterIsInstance<BotMessage.Image>()

        if (imageMessages.isEmpty()) {
            context.sender.sendText("请发送 'WM价格'+'查询部件的售卖部分的截图' 来查询部件在WM的白金价格")
            return
        }

        if (imageMessages.size > 2) {
            context.sender.sendText("单次查询图片上限为2张")
            return
        }
        val wordsList = mutableListOf<String>()
        coroutineScope {
            imageMessages.map { imageMessage ->
                async {
                    aipOcrClient.getBasicOcr(mapOf("url" to imageMessage.url))
                }
            }.awaitAll().forEach { result ->
                val root = JacksonUtil.readTree(result.toString())
                val wordsArray = root["words_result"]
                wordsList.addAll(
                    wordsArray.mapNotNull { it["words"]?.asText() }
                        .map { word -> word.replace(Regex("""^\d+\s*[xX×Ⅹ]?\s*"""), "").trim() }
                )
            }
        }
        if (wordsList.isEmpty()) {
            context.sender.sendText("未识别到任何部件名称，请重新发送尽可能清晰的部件售卖图")
            return
        }
        val wfMarketItemEntityList = wfMarketItemService.selectListZhNameList(wordsList)
        if (wfMarketItemEntityList.isNullOrEmpty()) {
            context.sender.sendText("未查询到任何匹配的部件，请重新发送尽可能清晰的部件售卖图")
            return
        }
        context.sender.sendText("正在查询 ${wfMarketItemEntityList.size} 个部件的 WM 最低售价，请耐心等待...")
        val results = coroutineScope {
            wfMarketItemEntityList.map { item ->
                async {
                    wmRateLimiter.acquire()
                    try {
                        val price = parseDataUtil.parseWmMinimalPrice(item.urlName!!)
                        val ducats = item.ducats ?: 0
                        val ratio = if (price > 0) ducats.toDouble() / price else 0.0
                        WmDucats(item.zhName, ducats, price, ratio)
                    } finally {
                        wmRateLimiter.release()
                    }
                }
            }.awaitAll()
        }
        val imgData = WebImgUtil.ImgData(
            url = "http://${webImgUtil.frontendAddress}/wmPrice",
            imgName = "wmPrice-${UUID.randomUUID()}",
            data = JacksonUtil.toJsonString(results),
            element = "#app",
            waitElement = ".wmPrice"
        )

        val url = webImgUtil.getImgUrl(imgData)
        context.sender.sendImage(url)
        webImgUtil.deleteImg(imgData = imgData)
    }
}
