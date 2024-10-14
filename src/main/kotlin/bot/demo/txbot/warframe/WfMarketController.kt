package bot.demo.txbot.warframe

import bot.demo.txbot.common.botUtil.BotUtils.Context
import bot.demo.txbot.common.utils.OtherUtil
import bot.demo.txbot.common.utils.UrlUtil.urlEncode
import bot.demo.txbot.common.utils.WebImgUtil
import bot.demo.txbot.warframe.warframeResp.WarframeRespEnum
import bot.demo.txbot.other.distribute.annotation.AParameter
import bot.demo.txbot.other.distribute.annotation.ActionService
import bot.demo.txbot.other.distribute.annotation.Executor
import bot.demo.txbot.warframe.WfMarketController.WfMarket.lichOrderEntity
import bot.demo.txbot.warframe.database.WfLexiconService
import bot.demo.txbot.warframe.database.WfRivenService
import bot.demo.txbot.warframe.vo.WfMarketVo
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import java.util.*
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
    @Qualifier("otherUtil") private val otherUtil: OtherUtil
) {
    object WfMarket {
        var lichOrderEntity: WfMarketVo.LichEntity? = null
        var rivenOrderList: WfMarketVo.RivenOrderList? = null
    }

    @AParameter
    @Executor(action = "(?i)\\bwm\\s*(\\S+.*)$")
    fun getMarketItem(context: Context, matcher: Matcher) {
        val key = matcher.group(1)
        val regex = """(\d+)(?=级)|(满级)""".toRegex()
        val matchResult = regex.find(key)
        val level = matchResult?.value

        // 移除匹配到的部分并去除多余的空格
        val cleanKey = matchResult?.let { key.replace("${it.value}级", "").replace("满级", "").trim() } ?: key
        val itemEntity = wfLexiconService.turnKeyToUrlNameByLexicon(cleanKey)

        if (itemEntity != null) {
            wfUtil.sendMarketItemInfo(context, itemEntity, level)
            return
        }

        val keyList = wfLexiconService.turnKeyToUrlNameByLexiconLike(cleanKey)
        if (!keyList.isNullOrEmpty()) {
            wfUtil.sendMarketItemInfo(context, keyList.first()!!, level)
            return
        }

        val fuzzyList = mutableListOf<String>()
        key.forEach { eachKey ->
            wfLexiconService.fuzzyQuery(eachKey.toString())?.forEach {
                it?.zhItemName?.let { name -> fuzzyList.add(name) }
            }
        }

        if (fuzzyList.isNotEmpty()) {
            otherUtil.findMatchingStrings(key, fuzzyList).let {
                context.sendMsg(WarframeRespEnum.SEARCH_NOT_FOUND.message + it.joinToString(", "))
            }
        } else context.sendMsg(WarframeRespEnum.SEARCH_MATCH_NOT_FOUND.message)
    }

    @AParameter
    @Executor(action = "(?i)\\b(wr|wmr)\\s*(\\S+.*)$")
    fun getRiven(context: Context, matcher: Matcher) {
        val key = matcher.group(2)
        val parameterList = key.split(" ")

        // 正则匹配紫卡循环次数
        val pattern = """(?<=\D)\d+(?=洗)""".toRegex()
        val matchResult = pattern.find(key)
        val reRollTimes = matchResult?.value?.toInt()

        val itemNameKey: String = parameterList.first()
        val itemEntity = wfRivenService.turnKeyToUrlNameByLich(itemNameKey)
            ?: wfRivenService.searchByRivenLike(itemNameKey).firstOrNull()
            ?: run {
                wfUtil.handleFuzzySearch(context, itemNameKey)
                return
            }

        val rivenJson = wfUtil.getAuctionsJson(
            parameterList = parameterList,
            itemEntityUrlName = itemEntity.urlName,
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

    @AParameter
    @Executor(action = "(?i)\\bwl\\s*(\\S+.*)$")
    fun getLich(context: Context, matcher: Matcher) {
        val key = matcher.group(1)
        val parameterList = key.split(" ")

        val regex = """\d+""".toRegex()
        val matchResult = regex.find(key)
        val damage = matchResult?.value?.toInt()

        val itemNameKey: String = parameterList.first()
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
        val lichType = if (itemEntity.urlName.contains("kuva")) "lich" else "sister"

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

        lichOrderEntity = WfMarketVo.LichEntity(
            lichName = itemEntity.zhName!!,
            lichOrderInfoList = rivenOrderList,
        )

        val imgData = WebImgUtil.ImgData(
            url = "http://localhost:16666/lich",
            imgName = "lich-${UUID.randomUUID()}",
            element = "body"
        )

        webImgUtil.sendNewImage(context, imgData)
        webImgUtil.deleteImg(imgData = imgData)
    }

    @AParameter
    @Executor(action = "wiki (.*)")
    fun getWikiUrl(context: Context, matcher: Matcher) {
        val key = matcher.group(1)
        val wikiUrl = "https://warframe.huijiwiki.com/wiki/${key.urlEncode()}"
        context.sendMsg(WarframeRespEnum.SEARCH_WIKI.message + wikiUrl)
    }
}