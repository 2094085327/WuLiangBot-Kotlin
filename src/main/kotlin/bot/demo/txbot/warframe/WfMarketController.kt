package bot.demo.txbot.warframe

import bot.demo.txbot.common.utils.UrlUtil.urlEncode
import bot.demo.txbot.warframe.database.WfLexiconService
import bot.demo.txbot.warframe.database.WfRivenService
import com.mikuac.shiro.annotation.AnyMessageHandler
import com.mikuac.shiro.annotation.MessageHandlerFilter
import com.mikuac.shiro.annotation.common.Shiro
import com.mikuac.shiro.core.Bot
import com.mikuac.shiro.dto.event.message.AnyMessageEvent
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.util.regex.Matcher
import java.util.regex.Pattern


/**
 * @description: Warframe 市场
 * @author Nature Zero
 * @date 2024/5/20 上午9:03
 */
@Shiro
@Component
class WfMarketController @Autowired constructor(
    private val wfUtil: WfUtil
) {
    @Autowired
    final lateinit var wfLexiconService: WfLexiconService

    @Autowired
    final lateinit var wfRivenService: WfRivenService

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
        val modRank: Int,
        val reRolls: Int,
        val startPlatinum: Int,
        val buyOutPlatinum: Int,
        val polarity: String,
        val positive: List<Attributes>,
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

    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "wm (.*)")
    fun getMarketItem(bot: Bot, event: AnyMessageEvent, matcher: Matcher) {
        val key = matcher.group(1)
        val regex = """(\d+)(?=级)|(满级)""".toRegex()
        val matchResult = regex.find(key)
        val level = matchResult?.value

        // 移除匹配到的部分并去除多余的空格
        val cleanKey = matchResult?.let { key.replace("${it.value}级", "").replace("满级", "").trim() } ?: key
        val itemEntity = wfLexiconService.turnKeyToUrlNameByLexicon(cleanKey)

        if (itemEntity != null) {
            wfUtil.sendMarketItemInfo(bot, event, itemEntity, level)
            return
        }

        val keyList = wfLexiconService.turnKeyToUrlNameByLexiconLike(cleanKey)
        if (!keyList.isNullOrEmpty()) {
            wfUtil.sendMarketItemInfo(bot, event, keyList.first()!!, level)
            return
        }

        val fuzzyList = mutableListOf<String>()
        key.forEach { eachKey ->
            wfLexiconService.fuzzyQuery(eachKey.toString())?.forEach {
                it?.zhItemName?.let { name -> fuzzyList.add(name) }
            }
        }

        if (fuzzyList.isNotEmpty()) {
            wfUtil.findMatchingStrings(key, fuzzyList).let {
                bot.sendMsg(event, "未找到该物品,也许你想找的是:[${it.joinToString(", ")}]", false)
            }
        } else {
            bot.sendMsg(event, "未找到任何匹配项。", false)
        }
    }


    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "wr (.*)")
    fun getRiven(bot: Bot, event: AnyMessageEvent, matcher: Matcher) {
        val key = matcher.group(1)
        val parameterList = key.split(" ")

        val pattern = Pattern.compile("(?<=\\D)\\d+(?=洗)")

        // 创建Matcher对象
        val match = pattern.matcher(key)

        val reRollTimes = if (!match.find())  null else match.group().toInt()

        val itemNameKey: String = parameterList.first()
        val itemEntity = wfRivenService.turnKeyToUrlNameByRiven(itemNameKey)

        if (itemEntity == null) {
            // 如果未找到物品，则执行模糊搜索
            wfUtil.handleFuzzySearch(bot, event, itemNameKey)
            return
        }
        val rivenJson = wfUtil.getAuctionsJson(
            parameterList = parameterList,
            itemEntity = itemEntity,
            auctionType = "riven",
        )

        if (rivenJson == null) {
            bot.sendMsg(event, "查询失败，请稍后重试", false)
            return
        }

        // 筛选和格式化拍卖数据
        val auctionInfo = wfUtil.formatAuctionData(rivenJson, itemEntity.zhName!!,reRollTimes)

        bot.sendMsg(event, auctionInfo, false)
    }

    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "wl (.*)")
    fun getLich(bot: Bot, event: AnyMessageEvent, matcher: Matcher) {
        val key = matcher.group(1)
        val parameterList = key.split(" ")

        val itemNameKey: String = parameterList.first()
        val itemEntity = wfRivenService.turnKeyToUrlNameByLich(itemNameKey)

        if (itemEntity == null) {
            wfUtil.handleFuzzySearch(bot, event, itemNameKey)
            return
        }

        val otherPrams = parameterList.drop(1)
        val element: String? = otherPrams.firstOrNull { !it.matches(Regex("([有无])")) }
        val ephemera: String? = otherPrams.firstOrNull { it.contains("无") || it.contains("有") }

        val urlElement: String? = element?.let { wfLexiconService.getOtherName(it) }

        val lichType = if (itemNameKey.contains("赤毒")) "lich" else "sister"

        val lichJson = wfUtil.getAuctionsJson(
            element = urlElement,
            ephemera = ephemera,
            itemEntity = itemEntity,
            auctionType = "lich",
            lichType = lichType
        )

        if (lichJson == null) {
            bot.sendMsg(event, "查询失败，请稍后重试", false)
            return
        }

        // 筛选和格式化拍卖数据
        val auctionInfo = wfUtil.formatLichAuctionData(lichJson, itemEntity.zhName!!)
        bot.sendMsg(event, auctionInfo, false)
    }

    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "wiki (.*)")
    fun getWikiUrl(bot: Bot, event: AnyMessageEvent, matcher: Matcher) {
        val key = matcher.group(1)
        val wikiUrl = "https://warframe.huijiwiki.com/wiki/${key.urlEncode()}"
        bot.sendMsg(event, "你查询的物品的wiki地址可能是:$wikiUrl", false)
    }
}