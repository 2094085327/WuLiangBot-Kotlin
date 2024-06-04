package bot.demo.txbot.warframe

import bot.demo.txbot.common.utils.HttpUtil
import bot.demo.txbot.warframe.database.WfLexiconEntity
import bot.demo.txbot.warframe.database.WfLexiconService
import bot.demo.txbot.warframe.database.WfRivenEntity
import bot.demo.txbot.warframe.database.WfRivenService
import com.fasterxml.jackson.databind.JsonNode
import com.mikuac.shiro.annotation.AnyMessageHandler
import com.mikuac.shiro.annotation.MessageHandlerFilter
import com.mikuac.shiro.annotation.common.Shiro
import com.mikuac.shiro.core.Bot
import com.mikuac.shiro.dto.event.message.AnyMessageEvent
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import pers.wuliang.robot.common.utils.LoggerUtils.logError
import java.util.regex.Matcher


/**
 * @description: Warframe 市场
 * @author Nature Zero
 * @date 2024/5/20 上午9:03
 */
@Shiro
@Component
class WfMarketController {
    @Autowired
    lateinit var wfLexiconService: WfLexiconService

    @Autowired
    lateinit var wfRivenService: WfRivenService

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

    data class LichOrderInfo(
        val element: String,
        val havingEphemera: Boolean,
        val damage: Int,
        val startPlatinum: Int,
        val buyOutPlatinum: Int,
    )

    /**
     * 发送物品信息
     *
     * @param bot 机器人
     * @param event 事件
     * @param item 物品
     * @param modLevel 模组等级
     */
    fun sendMarketItemInfo(bot: Bot, event: AnyMessageEvent, item: WfLexiconEntity, modLevel: Any? = null) {
        val url = "$WARFRAME_MARKET_ITEMS/${item.urlName}/orders"
        val headers = mutableMapOf<String, Any>("accept" to "application/json")
        val marketJson = HttpUtil.doGetJson(url = url, headers = headers)

        // 定义允许的状态集合
        val allowedStatuses = setOf("online", "ingame")
        val orders = marketJson["payload"]["orders"]

        // 获取所有订单中的最大 mod_rank 值
        val maxModRank = if (modLevel == "满级" && orders.any { it.has("mod_rank") }) {
            orders.filter { it.has("mod_rank") }.maxOfOrNull { it["mod_rank"].intValue() }
        } else {
            (modLevel as? String)?.toIntOrNull()
        }

        // 筛选出符合条件的订单
        val filteredOrders = orders.asSequence()
            .filter { order ->
                order["order_type"].textValue() == "sell" &&
                        order["user"]["status"].textValue() in allowedStatuses &&
                        (modLevel == null || (order.has("mod_rank") &&
                                order["mod_rank"].intValue() == maxModRank))
            }
            .sortedBy { it["platinum"].intValue() }
            .take(5)
            .map {
                OrderInfo(
                    platinum = it["platinum"].intValue(),
                    quantity = it["quantity"].intValue(),
                    inGameName = it["user"]["ingame_name"].textValue()
                )
            }
            .toList()

        val orderString = if (filteredOrders.isEmpty()) {
            "当前没有任何在线的玩家出售${item.zhItemName}"
        } else {
            filteredOrders.joinToString("\n") {
                "${it.inGameName} 价格: ${it.platinum} 数量: ${it.quantity}"
            } + "\n/w ${filteredOrders.first().inGameName} Hi! I want to buy: \"${item.enItemName}\" for ${filteredOrders.first().platinum} platinum.(warframe market)"
        }

        val modLevelString = when {
            modLevel == "满级" -> "满级"
            modLevel != null -> "${modLevel}级"
            else -> ""
        }

        bot.sendMsg(
            event,
            "你查询的物品是 $modLevelString「${item.zhItemName}」\n$orderString",
            false
        )
    }

    fun findMatchingStrings(key: String, keyList: List<String>): List<String> {
        // 统计key中每个字符的频率
        val keyFrequency = HashMap<Char, Int>()
        key.forEach { char ->
            keyFrequency[char] = keyFrequency.getOrDefault(char, 0) + 1
        }

        val map = HashMap<String, Int>()
        keyList.forEach { eachKey ->
            // 统计eachKey中每个字符的频率
            val eachKeyFrequency = HashMap<Char, Int>()
            eachKey.forEach { char ->
                eachKeyFrequency[char] = eachKeyFrequency.getOrDefault(char, 0) + 1
            }

            // 计算两个频率映射的匹配程度
            var num = 0
            for ((char, freq) in keyFrequency) {
                num += minOf(freq, eachKeyFrequency.getOrDefault(char, 0))
            }

            map[eachKey] = num
        }

        // 将结果按匹配度降序排序并取前5个
        val sortedMap = map.toList().sortedByDescending { (_, value) -> value }.toMap()
        return sortedMap.keys.take(5).toList()
    }

    /**
     * 如果找不到项目，则处理模糊搜索的功能
     *
     * @param bot 机器人
     * @param event 事件
     * @param itemNameKey 物品名称关键字
     */
    fun handleFuzzySearch(bot: Bot, event: AnyMessageEvent, itemNameKey: String) {
        val fuzzyList = mutableSetOf<String>()
        itemNameKey.forEach { char ->
            wfRivenService.superFuzzyQuery(char.toString())
                ?.forEach { it?.zhName?.let { name -> fuzzyList.add(name) } }
        }

        if (fuzzyList.isNotEmpty()) {
            findMatchingStrings(itemNameKey, fuzzyList.toList()).let {
                bot.sendMsg(event, "未找到该物品,也许你想找的是:[${it.joinToString(", ")}]", false)
            }
        } else {
            bot.sendMsg(event, "未找到任何匹配项。", false)
        }
    }

    /**
     * 处理参数并分离正负词条的功能
     *
     * @param parameters 参数
     * @param positiveStats 正词条
     * @param negativeStat 负词条
     */
    fun processParameters(
        parameters: List<String>,
        positiveStats: MutableList<String>,
        negativeStat: String?
    ): String? {
        var negative = negativeStat

        parameters.forEach { parameter ->
            if (parameter.contains("负")) {
                negative = if (parameter == "无负") "none"
                else wfRivenService.turnKeyToUrlNameByRivenLike(parameter.replace("负", ""))?.firstOrNull()?.urlName
            } else {
                wfRivenService.turnKeyToUrlNameByRivenLike(parameter)
                    ?.firstOrNull()?.urlName?.let { positiveStats.add(it) }
            }
        }

        return negative
    }


    /**
     * 用于为API调用创建查询参数的函数
     *
     * @param weaponUrlName 武器URL名称
     * @param positiveStats 正词条
     * @param negativeStat 负词条
     * @return 查询参数
     */
    fun createQueryParams(
        weaponUrlName: String,
        positiveStats: List<String>,
        negativeStat: String?
    ): MutableMap<String, String> {
        return mutableMapOf(
            "weapon_url_name" to weaponUrlName,
            "sort_by" to "price_asc"
        ).apply {
            if (positiveStats.isNotEmpty()) {
                put("positive_stats", positiveStats.joinToString(","))
            }
            negativeStat?.let {
                put("negative_stats", it)
            }
        }
    }

    /**
     * 用于为API调用创建查询参数的函数
     *
     * @param weaponUrlName 武器URL名称
     * @param element 元素
     * @param ephemera 是否有幻纹
     * @return 查询参数
     */
    fun createLichQueryParams(
        weaponUrlName: String,
        element: String? = null,
        ephemera: Boolean? = false
    ): MutableMap<String, String> {
        return mutableMapOf(
            "weapon_url_name" to weaponUrlName,
            "sort_by" to "price_asc"
        ).apply {
            if (ephemera != null) put("having_ephemera", ephemera.toString())
            if (element != null) put("element", element)
        }
    }

    /**
     * 将拍卖数据格式化为字符串消息的函数
     *
     * @param rivenJson 紫卡Json数据
     * @param itemZhName 物品中文名称
     * @return 格式化后的拍卖数据
     */
    fun formatAuctionData(rivenJson: JsonNode, itemZhName: String): String {
        val orders = rivenJson["payload"]["auctions"]
        val allowedStatuses = setOf("online", "ingame")

        val rivenOrderList = orders.asSequence()
            .filter { it["owner"]["status"].textValue() in allowedStatuses }
            .take(5)
            .map { order ->
                RivenOrderInfo(
                    modRank = order["item"]["mod_rank"].intValue(),
                    reRolls = order["item"]["re_rolls"].intValue(),
                    startPlatinum = order["starting_price"]?.intValue() ?: order["buyout_price"].intValue(),
                    buyOutPlatinum = order["buyout_price"]?.intValue() ?: order["starting_price"].intValue(),
                    polarity = order["item"]["polarity"].textValue(),
                    positive = order["item"]["attributes"].map { attribute ->
                        Attributes(
                            value = attribute["value"].doubleValue(),
                            positive = attribute["positive"].booleanValue(),
                            urlName = attribute["url_name"].textValue()
                        )
                    }
                )
            }.toList()

        if (rivenOrderList.isEmpty()) {
            return "当前没有任何在线的玩家出售这种词条的${itemZhName}"
        }

        val polaritySymbols = mapOf("madurai" to "r", "vazarin" to "Δ", "naramon" to "-")
        val auctionDetails = rivenOrderList.joinToString("\n") { order ->
            val polaritySymbol = polaritySymbols[order.polarity] ?: "-"
            val attributes = order.positive.joinToString("|") { positive ->
                val sign = if (positive.positive) "+" else ""
                "${wfRivenService.turnUrlNameToKeyByRiven(positive.urlName)} $sign${positive.value}%"
            }
            "mod等级:${order.modRank} 起拍价:${order.startPlatinum} 一口价:${order.buyOutPlatinum} 循环次数:${order.reRolls} 极性:$polaritySymbol\n$attributes"
        }

        return "你查找的「${itemZhName}」紫卡前5条拍卖信息如下:\n$auctionDetails\n示例:wr 战刃 暴伤 暴击 负触发"
    }


    /**
     * 获取拍卖数据
     *
     * @param parameterList 紫卡参数列表
     * @param element 武器元素
     * @param ephemera 是否有幻纹
     * @param itemEntity 物品实体类
     * @param auctionType 拍卖类型
     * @param lichType 玄骸类型
     * @return Json数据
     */
    fun getAuctionsJson(
        parameterList: List<String>? = null,
        element: String? = null,
        ephemera: String? = null,
        itemEntity: WfRivenEntity,
        auctionType: String,
        lichType: String? = null
    ): JsonNode? {
        val positiveStats = mutableListOf<String>()
        val negativeStat: String? = null

        val queryParams = when (auctionType) {
            "riven" -> {
                processParameters(parameterList!!.drop(1), positiveStats, negativeStat)
                createQueryParams(itemEntity.urlName, positiveStats, negativeStat)
            }

            "lich" -> {
                val ephemeraBoolean = ephemera?.contains("有")
                createLichQueryParams(itemEntity.urlName, element = element, ephemera = ephemeraBoolean)
            }

            else -> return null
        }

        return try {
            when (auctionType) {
                "riven" -> HttpUtil.doGetJson(WARFRAME_MARKET_RIVEN_AUCTIONS, params = queryParams)
                "lich" -> when (lichType) {
                    "lich" -> HttpUtil.doGetJson(WARFRAME_MARKET_LICH_AUCTIONS, params = queryParams)
                    else -> HttpUtil.doGetJson(WARFRAME_MARKET_SISTER_AUCTIONS, params = queryParams)
                }

                else -> null
            }
        } catch (e: Exception) {
            logError("WM查询错误:$e")
            null
        }
    }


    /**
     * 将拍卖数据格式化为字符串消息的函数
     *
     * @param lichJson 紫卡Json数据
     * @param itemZhName 物品中文名称
     * @return 格式化后的拍卖数据
     */
    fun formatLichAuctionData(lichJson: JsonNode, itemZhName: String): String {
        val orders = lichJson["payload"]["auctions"]

        val rivenOrderList = orders.asSequence()
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

        return if (rivenOrderList.isEmpty()) {
            "当前没有任何在线的玩家出售这种词条的${itemZhName}"
        } else {
            val auctionDetails = rivenOrderList.joinToString("\n") { order ->
                "元素:${order.element} 起拍价:${order.startPlatinum} 一口价:${order.buyOutPlatinum} 有无幻纹:${if (!order.havingEphemera) "无" else "有"} 伤害:${order.damage}"
            }
            "你查找的「${itemZhName}」玄骸武器前5条拍卖信息如下:\n$auctionDetails\n示例:wl 信条·典客 火 无幻纹"
        }
    }

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
            sendMarketItemInfo(bot, event, itemEntity, level)
            return
        }

        val keyList = wfLexiconService.turnKeyToUrlNameByLexiconLike(cleanKey)
        if (!keyList.isNullOrEmpty()) {
            sendMarketItemInfo(bot, event, keyList.last()!!, level)
            return
        }

        val fuzzyList = mutableListOf<String>()
        key.forEach { eachKey ->
            wfLexiconService.fuzzyQuery(eachKey.toString())?.forEach {
                it?.zhItemName?.let { name -> fuzzyList.add(name) }
            }
        }

        if (fuzzyList.isNotEmpty()) {
            findMatchingStrings(key, fuzzyList).let {
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

        val itemNameKey: String = parameterList.first()
        val itemEntity = wfRivenService.turnKeyToUrlNameByRiven(itemNameKey)

        if (itemEntity == null) {
            // 如果未找到物品，则执行模糊搜索
            handleFuzzySearch(bot, event, itemNameKey)
            return
        }
        val rivenJson = getAuctionsJson(
            parameterList = parameterList,
            itemEntity = itemEntity,
            auctionType = "riven",
        )

        if (rivenJson == null) {
            bot.sendMsg(event, "查询失败，请稍后重试", false)
            return
        }

        // 筛选和格式化拍卖数据
        val auctionInfo = formatAuctionData(rivenJson, itemEntity.zhName!!)

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
            handleFuzzySearch(bot, event, itemNameKey)
            return
        }

        val otherPrams = parameterList.drop(1)
        val element: String? = otherPrams.firstOrNull { !it.matches(Regex("([有无])")) }
        val ephemera: String? = otherPrams.firstOrNull { it.contains("无") || it.contains("有") }

        val urlElement: String? = element?.let { wfLexiconService.getOtherName(it) }

        val lichType = if (itemNameKey.contains("赤毒")) "lich" else "sister"

        val lichJson = getAuctionsJson(
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
        val auctionInfo = formatLichAuctionData(lichJson, itemEntity.zhName!!)
        bot.sendMsg(event, auctionInfo, false)
    }
}