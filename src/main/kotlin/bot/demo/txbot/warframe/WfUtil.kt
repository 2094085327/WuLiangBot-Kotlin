package bot.demo.txbot.warframe

import bot.demo.txbot.common.utils.HttpUtil
import bot.demo.txbot.common.utils.LoggerUtils.logError
import bot.demo.txbot.common.utils.OtherUtil
import bot.demo.txbot.warframe.WfMarketController.*
import bot.demo.txbot.warframe.database.WfLexiconEntity
import bot.demo.txbot.warframe.database.WfRivenEntity
import bot.demo.txbot.warframe.database.WfRivenService
import com.fasterxml.jackson.databind.JsonNode
import com.mikuac.shiro.core.Bot
import com.mikuac.shiro.dto.event.message.AnyMessageEvent
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit


/**
 * @description: Warframe 工具类
 * @author Nature Zero
 * @date 2024/6/4 上午9:45
 */
@Component
class WfUtil @Autowired constructor(
    val wfRivenService: WfRivenService,
    @Qualifier("otherUtil") private val otherUtil: OtherUtil
) {

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
                "| ${it.inGameName} \n" + "| 价格: ${it.platinum} 数量: ${it.quantity}\n"
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
            otherUtil.findMatchingStrings(itemNameKey, fuzzyList.toList()).let {
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
     * 对字符串首字符进行转大写
     *
     * @return 首字符转换为大写后的字符串
     */
    fun String.capitalizeSpecial(): String {
        // 将字符串分割为单词，使用空格和 '-' 作为分隔符
        val words = this.split(" ", "-")

        // 对每个单词的首字母进行大写处理
        val capitalizedWords = words.joinToString(" ") { word ->
            if (word.isNotEmpty()) {
                word.replaceFirstChar { it.uppercase() }
            } else {
                word
            }
        }

        return capitalizedWords
    }

    /**
     * 判断输入的时间字符串距离当前时间有多远
     *
     * @param timeStr 时间字符串
     * @return 判断后的时间
     */
    fun timeAgo(timeStr: String): String {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
        val pastTime = LocalDateTime.parse(timeStr, formatter)
        val now = LocalDateTime.now()

        val years = ChronoUnit.YEARS.between(pastTime, now)
        val months = ChronoUnit.MONTHS.between(pastTime, now)
        val days = ChronoUnit.DAYS.between(pastTime, now)
        val hours = ChronoUnit.HOURS.between(pastTime, now)
        val minutes = ChronoUnit.MINUTES.between(pastTime, now)
        val seconds = ChronoUnit.SECONDS.between(pastTime, now)

        return when {
            years > 0 -> "${years}年前"
            months > 0 -> "${months}个月前"
            days > 0 -> "${days}天前"
            hours > 0 -> "${hours}小时前"
            minutes > 0 -> "${minutes}分钟前"
            seconds > 0 -> "${seconds}秒前"
            else -> "刚刚"
        }
    }

    /**
     * 将拍卖数据格式化为字符串消息的函数
     *
     * @param rivenJson 紫卡Json数据
     * @param itemZhName 物品中文名称
     * @param reRollTimes 紫卡循环次数
     * @return 格式化后的拍卖数据
     */
    fun formatAuctionData(rivenJson: JsonNode, itemZhName: String, reRollTimes: Int? = null): Any {
        val orders = rivenJson["payload"]["auctions"]
        val allowedStatuses = setOf("online", "ingame")
        val polaritySymbols = mapOf("madurai" to "r", "vazarin" to "Δ", "naramon" to "一")

        val rivenOrderList = orders.asSequence()
            // 筛选游戏状态为 在线 和 游戏中 的信息
            .filter {
                it["owner"]["status"].textValue() in allowedStatuses
                if (reRollTimes != null) it["item"]["re_rolls"].intValue() == reRollTimes else true
            }
            .take(5)
            .map { order ->
                RivenOrderInfo(
                    user = order["owner"]["ingame_name"].textValue(),
                    userStatus = if (order["owner"]["status"].textValue() == "online") "游戏在线" else "游戏中",
                    modName = (order["item"]["weapon_url_name"].textValue() + " " + order["item"]["name"].textValue()).capitalizeSpecial(),
                    modRank = order["item"]["mod_rank"].intValue(),
                    reRolls = order["item"]["re_rolls"].intValue(),
                    masteryLevel = order["item"]["mastery_level"].intValue(),
                    startPlatinum = order["starting_price"]?.intValue() ?: order["buyout_price"].intValue(),
                    buyOutPlatinum = order["buyout_price"]?.intValue() ?: order["starting_price"].intValue(),
                    polarity = polaritySymbols[order["item"]["polarity"].textValue()] ?: "-",
                    positive = mutableListOf(),
                    negative = mutableListOf(),
                    updateTime = timeAgo(order["updated"].textValue())
                ).apply {
                    order["item"]["attributes"].forEach { attribute ->
                        val attr = Attributes(
                            value = attribute["value"].doubleValue(),
                            positive = attribute["positive"].booleanValue(),
                            urlName = wfRivenService.turnUrlNameToKeyByRiven(attribute["url_name"].textValue())
                        )
                        if (attr.positive) {
                            positive.add(attr)
                        } else {
                            negative.add(attr)
                        }
                    }
                }
            }.toList()

        if (rivenOrderList.isEmpty()) {
            return "当前没有任何在线的玩家出售这种词条的${itemZhName}"
        }

        WfMarket.rivenOrderList = RivenOrderList(
            itemName = itemZhName,
            orderList = rivenOrderList
        )

        return true
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

    interface Week {
        val week: Int
        var startTime: String?
        fun copyWithNewStartTime(newStartTime: String?): Week
    }

    data class OrdinaryWeek(override val week: Int, val items: List<String>, override var startTime: String?) : Week {
        override fun copyWithNewStartTime(newStartTime: String?): OrdinaryWeek {
            return copy(startTime = newStartTime)
        }
    }

    data class SteelItem(val name: String, val riven: Double?)
    data class SteelWeek(override val week: Int, val items: List<SteelItem>, override var startTime: String?) : Week {
        override fun copyWithNewStartTime(newStartTime: String?): SteelWeek {
            return copy(startTime = newStartTime)
        }
    }

    data class Data(val ordinary: List<OrdinaryWeek>, val steel: List<SteelWeek>)


    private val dateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME

    fun <T : Week> findCurrentWeek(weeks: List<T>, currentDate: LocalDateTime): T? {
        return weeks
            .sortedBy { it.week }
            .asSequence() // 使用序列，以便更高效地处理过滤和获取最后一项
            .filter { week ->
                week.startTime?.let { startTime ->
                    val startDateTime = OffsetDateTime.parse(startTime, dateTimeFormatter).toLocalDateTime()
                    startDateTime.isBefore(currentDate)
                } ?: true
            }
            .lastOrNull()
    }

    fun updateWeeks2(data: Data, currentDate: LocalDateTime): Data {
        // 更新周的开始时间
        fun <T : Week> updateWeekTimes(weeks: List<T>, maxTime: LocalDateTime?, maxIndex: Int): List<T> {
            if (maxTime == null) return weeks

            // 计算从最大时间到当前时间的天数
            val daysSinceMax = Duration.between(maxTime, currentDate).toDays().toInt()
            if (daysSinceMax <= 0) return weeks

            // 计算额外的周数
            val additionalWeeks = daysSinceMax / 7
            val weekCount = weeks.size
            // 计算更新后的起始周索引
            val startWeekIndex = (maxIndex + additionalWeeks % weekCount) % weekCount

            for (i in weeks.indices) {
                // 计算实际的索引
                val actualIndex = (startWeekIndex + i) % weekCount
                // 更新周的开始时间
                val updatedDateTime = maxTime.plusWeeks(additionalWeeks + i.toLong())
                val zonedDateTime = updatedDateTime.atZone(ZoneId.systemDefault())
                val offsetDateTime = zonedDateTime.toOffsetDateTime() // 转换为 OffsetDateTime
                // 使用 formatter 格式化并更新开始时间
                weeks[actualIndex].startTime = offsetDateTime.format(dateTimeFormatter)
            }
            return weeks
        }

        // 辅助函数 获取最大开始时间及其索引
        fun getMaxStartTimeAndIndex(weeks: List<Week>): Pair<Int, LocalDateTime> {
            return weeks.withIndex().maxByOrNull { (_, week) ->
                week.startTime?.let { startTime ->
                    OffsetDateTime.parse(startTime, dateTimeFormatter).toLocalDateTime()
                }
                    ?: LocalDateTime.MIN
            }?.let { (index, week) ->
                index to (week.startTime?.let { startTime ->
                    OffsetDateTime.parse(startTime, dateTimeFormatter).toLocalDateTime()
                } ?: LocalDateTime.MIN)
            } ?: (0 to LocalDateTime.MIN)
        }

        // 获取最大钢铁开始时间及其索引
        val (maxSteelIndex, maxSteelStartTime) = getMaxStartTimeAndIndex(data.steel)

        // 获取最大普通开始时间及其索引
        val (maxOrdinaryIndex, maxOrdinaryStartTime) = getMaxStartTimeAndIndex(data.ordinary)

        // 更新数据中的 steel 和 ordinary 列表
        return data.copy(
            steel = updateWeekTimes(data.steel, maxSteelStartTime, maxSteelIndex),
            ordinary = updateWeekTimes(data.ordinary, maxOrdinaryStartTime, maxOrdinaryIndex)
        )
    }


    /**
     * 定义一个扩展函数，用于将UTC时间字符串转换为东八区的时间字符串
     *
     * @return 东八时区时间字符串
     */
    object WfUtilObject {
        fun String.toEastEightTimeZone(): String {
            // 解析 UTC 时间字符串为 ZonedDateTime
            val utcTime = ZonedDateTime.parse(this, DateTimeFormatter.ISO_DATE_TIME)

            // 转换为东八区，即中国标准时间
            val targetZoneId = ZoneId.of("Asia/Shanghai")
            val targetTime = utcTime.withZoneSameInstant(targetZoneId)

            // 格式化输出
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            return targetTime.format(formatter)
        }
    }
}