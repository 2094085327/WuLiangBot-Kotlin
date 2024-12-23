package bot.demo.txbot.warframe

import bot.demo.txbot.common.botUtil.BotUtils.Context
import bot.demo.txbot.common.logAop.SystemLog
import bot.demo.txbot.common.utils.OtherUtil
import bot.demo.txbot.common.utils.RedisService
import bot.demo.txbot.common.utils.UrlUtil.urlEncode
import bot.demo.txbot.common.utils.WebImgUtil
import bot.demo.txbot.other.distribute.annotation.AParameter
import bot.demo.txbot.other.distribute.annotation.ActionService
import bot.demo.txbot.other.distribute.annotation.Executor
import bot.demo.txbot.warframe.database.WfLexiconEntity
import bot.demo.txbot.warframe.database.WfLexiconService
import bot.demo.txbot.warframe.database.WfRivenService
import bot.demo.txbot.warframe.vo.WfMarketVo
import bot.demo.txbot.warframe.warframeResp.WarframeRespEnum
import org.springframework.beans.factory.annotation.Autowired
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
class WfMarketController @Autowired constructor(
    private val wfUtil: WfUtil,
    private val webImgUtil: WebImgUtil,
    private val wfLexiconService: WfLexiconService,
    private val wfRivenService: WfRivenService,
    @Qualifier("otherUtil") private val otherUtil: OtherUtil,
    private val redisService: RedisService
) {
    object WfMarket {
        var rivenOrderList: WfMarketVo.RivenOrderList? = null
    }

    @SystemLog(businessName = "获取WM市场物品信息")
    @AParameter
    @Executor(action = "(?i)\\bwm\\s*(\\S+.*)$")
    fun getMarketItem(context: Context, matcher: Matcher) {
        val key = matcher.group(1)
        val regex = """(\d+)(?=级)|(满级)""".toRegex()
        val matchResult = regex.find(key)
        val level = matchResult?.value

        // 移除匹配到的部分并去除多余的空格
        val cleanKey = matchResult?.let { key.replace("${it.value}级", "").replace("满级", "").trim() } ?: key
        val redisKey = "warframe:lexicon:$cleanKey"

        // 尝试从Redis获取数据
        val lexiconEntity = redisService.getValue(redisKey, WfLexiconEntity::class.java)
        if (lexiconEntity != null) {
            wfUtil.sendMarketItemInfo(context, lexiconEntity, level)
            return
        }

        // Redis中没有数据，从数据库中查询
        val itemEntity = wfUtil.fetchItemEntity(cleanKey)
            ?: run {
                // 模糊查询
                val fuzzyList = key.flatMap { eachKey ->
                    wfLexiconService.fuzzyQuery(eachKey.toString())?.mapNotNull { it?.zhItemName } ?: emptyList()
                }
                if (fuzzyList.isNotEmpty()) {
                    otherUtil.findMatchingStrings(key, fuzzyList).let {
                        context.sendMsg(WarframeRespEnum.SEARCH_NOT_FOUND.message + it.joinToString(", "))
                    }
                } else context.sendMsg(WarframeRespEnum.SEARCH_MATCH_NOT_FOUND.message)
                return
            }
        wfUtil.sendMarketItemInfo(context, itemEntity, level)
        return
    }

    @SystemLog(businessName = "获取WM市场紫卡信息")
    @AParameter
    @Executor(action = "(?i)\\b(wr|wmr)\\s*(\\S+.*)$")
    fun getRiven(context: Context, matcher: Matcher) {
        val key = matcher.group(2)
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

        val rivenJson = wfUtil.getAuctionsJson(
            parameterList = parameterList,
            itemEntityUrlName = itemEntity.urlName!!,
            auctionType = "riven",
        )

        if (rivenJson == null) {
            context.sendMsg(WarframeRespEnum.SEARCH_ERROR.message)
            return
        }

        // 筛选和格式化拍卖数据
        val auctionInfo = wfUtil.formatAuctionData(rivenJson, itemEntity.zhName!!, reRollTimes)
        if (!auctionInfo) {
            context.sendMsg(WarframeRespEnum.SEARCH_RIVEN_NOT_FOUND.message + itemEntity.zhName)
            return
        }

        val imgData = WebImgUtil.ImgData(
            url = "http://localhost:16666/riven",
            imgName = "riven-${UUID.randomUUID()}",
            element = "body"
        )

        webImgUtil.sendNewImage(context, imgData)
        webImgUtil.deleteImg(imgData = imgData)

    }

    @SystemLog(businessName = "获取WM市场玄骸武器信息")
    @AParameter
    @Executor(action = "(?i)\\bwl\\s*(\\S+.*)$")
    fun getLich(context: Context, matcher: Matcher) {
        val key = matcher.group(1)
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
        val lichType = if (itemEntity.urlName!!.contains("kuva")) "lich" else "sister"

        if (!redisService.hasKey("warframe:lichOrderEntity:${itemEntity.urlName}${damage}${element}${ephemera}")) {
            val lichJson = wfUtil.getAuctionsJson(
                element = urlElement,
                ephemera = ephemera,
                itemEntityUrlName = itemEntity.urlName,
                auctionType = "lich",
                lichType = lichType
            )

            if (lichJson == null) {
                context.sendMsg(WarframeRespEnum.SEARCH_ERROR.message)
                return
            }

            // 筛选和格式化拍卖数据
            val orders = lichJson["payload"]["auctions"]

            val rivenOrderList = orders.asSequence()
                .filter { if (damage != null) it["item"]["damage"].intValue() == damage else true }
                .take(5)
                .map { order ->
                    WfMarketVo.LichOrderInfo(
                        element = wfLexiconService.getOtherEnName(order["item"]["element"].textValue())!!,
                        havingEphemera = order["item"]["having_ephemera"].booleanValue(),
                        damage = order["item"]["damage"].intValue(),
                        startPlatinum = order["starting_price"]?.intValue() ?: order["buyout_price"].intValue(),
                        buyOutPlatinum = order["buyout_price"]?.intValue() ?: order["starting_price"].intValue(),
                    )
                }.toList()

            val lichOrderEntity = WfMarketVo.LichEntity(
                lichName = itemEntity.zhName!!,
                lichOrderInfoList = rivenOrderList,
            )

            redisService.setValueWithExpiry(
                "warframe:lichOrderEntity:${itemEntity.urlName}${damage}${element}${ephemera}",
                lichOrderEntity,
                60L,
                TimeUnit.SECONDS
            )
        }

        val imgData = WebImgUtil.ImgData(
            url = "http://localhost:16666/lich?url_name=${itemEntity.urlName}&damage=${damage}&element=${element}&ephemera=${ephemera}",
            imgName = "lich-${UUID.randomUUID()}",
            element = "body"
        )

        webImgUtil.sendNewImage(context, imgData)
        webImgUtil.deleteImg(imgData = imgData)
    }

    @SystemLog(businessName = "获取WM市场紫卡均价")
    @AParameter
    @Executor(action = "\\b(紫卡价格|紫卡排行|紫卡|紫卡均价)\\b")
    fun getRivenRanking(context: Context, matcher: Matcher) {
        wfUtil.getAllRivenPriceTiming()
        val imgData = WebImgUtil.ImgData(
            url = "http://localhost:16666/allRivenPrice",
            imgName = "allRivenPrice-${UUID.randomUUID()}",
            element = "body"
        )
        webImgUtil.sendNewImage(context, imgData)
        webImgUtil.deleteImg(imgData = imgData)
    }

    @SystemLog(businessName = "获取物品Wiki链接")
    @AParameter
    @Executor(action = "wiki (.*)")
    fun getWikiUrl(context: Context, matcher: Matcher) {
        val key = matcher.group(1)
        val wikiUrl = "https://warframe.huijiwiki.com/wiki/${key.urlEncode()}"
        context.sendMsg(WarframeRespEnum.SEARCH_WIKI.message + wikiUrl)
    }
}