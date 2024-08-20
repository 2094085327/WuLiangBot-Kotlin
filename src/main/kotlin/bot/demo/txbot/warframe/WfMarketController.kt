package bot.demo.txbot.warframe

import bot.demo.txbot.common.botUtil.BotUtils.ContextProvider
import bot.demo.txbot.common.utils.OtherUtil
import bot.demo.txbot.common.utils.UrlUtil.urlEncode
import bot.demo.txbot.common.utils.WebImgUtil
import bot.demo.txbot.warframe.WfMarketController.WfMarket.lichOrderEntity
import bot.demo.txbot.warframe.database.WfLexiconService
import bot.demo.txbot.warframe.database.WfRivenService
import com.mikuac.shiro.annotation.AnyMessageHandler
import com.mikuac.shiro.annotation.MessageHandlerFilter
import com.mikuac.shiro.annotation.common.Shiro
import com.mikuac.shiro.core.Bot
import com.mikuac.shiro.dto.event.message.AnyMessageEvent
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.util.*
import java.util.regex.Matcher


/**
 * @description: Warframe 市场
 * @author Nature Zero
 * @date 2024/5/20 上午9:03
 */
@Shiro
@Component
class WfMarketController @Autowired constructor(
    private val wfUtil: WfUtil,
    private val webImgUtil: WebImgUtil,
    private val wfLexiconService: WfLexiconService,
    private val wfRivenService: WfRivenService
) {

    /**
     * Warframe 市场物品
     *
     *
     * @property platinum 价格
     * @property quantity 数量
     * @property inGameName 游戏内名称
     */
    data class OrderInfo(
        val platinum: Int,
        val quantity: Int,
        val inGameName: String,
    )

    /**
     * Warframe 紫卡信息
     *
     * @property value 属性值
     * @property positive 是否为正属性
     * @property urlName 属性URL名
     */
    data class Attributes(
        val value: Double,
        val positive: Boolean,
        val urlName: String
    )

    /**
     * Warframe 紫卡订单信息
     *
     * @property modRank mod等级
     * @property reRolls 循环次数
     * @property startPlatinum 起拍价格
     * @property buyOutPlatinum 一口价
     * @property polarity 极性
     * @property positive 属性
     */
    data class RivenOrderInfo(
        val user: String,
        val userStatus: String,
        val modName: String,
        val modRank: Int,
        val reRolls: Int,
        val masteryLevel: Int,
        val startPlatinum: Int,
        val buyOutPlatinum: Int,
        val polarity: String,
        val positive: MutableList<Attributes>,
        val negative: MutableList<Attributes>,
        val updateTime: String,
    )

    /**
     * 玄骸武器订单
     *
     * @property element 元素
     * @property havingEphemera 是否有幻纹
     * @property damage 伤害
     * @property startPlatinum 起拍价
     * @property buyOutPlatinum 一口价
     */
    data class LichOrderInfo(
        val element: String,
        val havingEphemera: Boolean,
        val damage: Int,
        val startPlatinum: Int,
        val buyOutPlatinum: Int,
    )

    data class LichEntity(
        val lichName: String,
        val lichOrderInfoList: List<LichOrderInfo>
    )

    data class RivenOrderList(
        val itemName: String,
        val orderList: List<RivenOrderInfo>
    )

    object WfMarket {
        var lichOrderEntity: LichEntity? = null
        var rivenOrderList: RivenOrderList? = null
    }


    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "wm (.*)")
    fun getMarketItem(bot: Bot, event: AnyMessageEvent, matcher: Matcher) {
        ContextProvider.initialize(event, bot)

        val key = matcher.group(1)
        val regex = """(\d+)(?=级)|(满级)""".toRegex()
        val matchResult = regex.find(key)
        val level = matchResult?.value

        // 移除匹配到的部分并去除多余的空格
        val cleanKey = matchResult?.let { key.replace("${it.value}级", "").replace("满级", "").trim() } ?: key
        val itemEntity = wfLexiconService.turnKeyToUrlNameByLexicon(cleanKey)

        if (itemEntity != null) {
            wfUtil.sendMarketItemInfo(itemEntity, level)
            return
        }

        val keyList = wfLexiconService.turnKeyToUrlNameByLexiconLike(cleanKey)
        if (!keyList.isNullOrEmpty()) {
            wfUtil.sendMarketItemInfo(keyList.first()!!, level)
            return
        }

        val fuzzyList = mutableListOf<String>()
        key.forEach { eachKey ->
            wfLexiconService.fuzzyQuery(eachKey.toString())?.forEach {
                it?.zhItemName?.let { name -> fuzzyList.add(name) }
            }
        }

        if (fuzzyList.isNotEmpty()) {
            OtherUtil().findMatchingStrings(key, fuzzyList).let {
                ContextProvider.sendMsg("未找到该物品,也许你想找的是:[${it.joinToString(", ")}]")
            }
        } else {
            ContextProvider.sendMsg("未找到任何匹配项。")
        }
    }


    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "\\b(wr|wmr)\\s+(\\S+.*)\$")
    fun getRiven(bot: Bot, event: AnyMessageEvent, matcher: Matcher) {
        ContextProvider.initialize(event, bot)

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
                wfUtil.handleFuzzySearch(itemNameKey)
                return
            }

        val rivenJson = wfUtil.getAuctionsJson(
            parameterList = parameterList,
            itemEntity = itemEntity,
            auctionType = "riven",
        )

        if (rivenJson == null) {
            ContextProvider.sendMsg("查询失败，请稍后重试")
            return
        }

        // 筛选和格式化拍卖数据
        val auctionInfo = wfUtil.formatAuctionData(rivenJson, itemEntity.zhName!!, reRollTimes)
        if (auctionInfo is String) ContextProvider.sendMsg(auctionInfo)

        val imgData = WebImgUtil.ImgData(
            url = "http://localhost:${webImgUtil.usePort}/warframe/riven",
            imgName = "riven-${UUID.randomUUID()}",
            element = "body"
        )

        webImgUtil.sendNewImage(imgData)
        webImgUtil.deleteImg(imgData = imgData)

    }


    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "wl (.*)")
    fun getLich(bot: Bot, event: AnyMessageEvent, matcher: Matcher) {
        ContextProvider.initialize(event, bot)

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
                wfUtil.handleFuzzySearch(itemNameKey)
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
            itemEntity = itemEntity,
            auctionType = "lich",
            lichType = lichType
        )

        if (lichJson == null) {
            ContextProvider.sendMsg("查询失败，请稍后重试")
            return
        }

        // 筛选和格式化拍卖数据
        val orders = lichJson["payload"]["auctions"]

        val rivenOrderList = orders.asSequence()
            .filter { if (damage != null) it["item"]["damage"].intValue() == damage else true }
            .take(5)
            .map { order ->
                LichOrderInfo(
                    element = wfLexiconService.getOtherEnName(order["item"]["element"].textValue())!!,
                    havingEphemera = order["item"]["having_ephemera"].booleanValue(),
                    damage = order["item"]["damage"].intValue(),
                    startPlatinum = order["starting_price"]?.intValue() ?: order["buyout_price"].intValue(),
                    buyOutPlatinum = order["buyout_price"]?.intValue() ?: order["starting_price"].intValue(),
                )
            }.toList()

        lichOrderEntity = LichEntity(
            lichName = itemEntity.zhName!!,
            lichOrderInfoList = rivenOrderList,
        )

        val imgData = WebImgUtil.ImgData(
            url = "http://localhost:${webImgUtil.usePort}/warframe/lich",
            imgName = "lich-${UUID.randomUUID()}",
            element = "body"
        )

        webImgUtil.sendNewImage(imgData)
        webImgUtil.deleteImg(imgData = imgData)
    }


    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "wiki (.*)")
    fun getWikiUrl(bot: Bot, event: AnyMessageEvent, matcher: Matcher) {
        ContextProvider.initialize(event, bot)

        val key = matcher.group(1)
        val wikiUrl = "https://warframe.huijiwiki.com/wiki/${key.urlEncode()}"
        ContextProvider.sendMsg("你查询的物品的wiki地址可能是:$wikiUrl")
    }
}