package bot.demo.txbot.warframe

import bot.demo.txbot.common.botUtil.BotUtils.Context
import bot.demo.txbot.common.utils.HttpUtil
import bot.demo.txbot.common.utils.LoggerUtils.logError
import bot.demo.txbot.common.utils.OtherUtil
import bot.demo.txbot.warframe.WfMarketController.WfMarket
import bot.demo.txbot.warframe.WfStatusController.WfStatus.replaceTime
import bot.demo.txbot.warframe.WfUtil.WfUtilObject.toEastEightTimeZone
import bot.demo.txbot.warframe.database.WfLexiconEntity
import bot.demo.txbot.warframe.database.WfRivenEntity
import bot.demo.txbot.warframe.database.WfRivenService
import bot.demo.txbot.warframe.vo.WfMarketVo
import bot.demo.txbot.warframe.vo.WfStatusVo
import bot.demo.txbot.warframe.vo.WfUtilVo
import com.fasterxml.jackson.databind.JsonNode
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
    private val wfRivenService: WfRivenService,
    @Qualifier("otherUtil") private val otherUtil: OtherUtil,
) {

    /**
     * 发送物品信息
     *
     * @param item 物品
     * @param modLevel 模组等级
     */
    fun sendMarketItemInfo(context: Context, item: WfLexiconEntity, modLevel: Any? = null) {
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
                WfMarketVo.OrderInfo(
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
                "| ${it.inGameName.replace(".", "ׅ")} \n" + "| 价格: ${it.platinum} 数量: ${it.quantity}\n"
            } + "\n/w ${
                filteredOrders.first().inGameName.replace(
                    ".",
                    "ׅ"
                )
            } Hi! I want to buy: \"${item.enItemName}\" for ${filteredOrders.first().platinum} platinum.(warframe market)"
        }

        val modLevelString = when {
            modLevel == "满级" -> "满级"
            modLevel != null -> "${modLevel}级"
            else -> ""
        }

        context.sendMsg("你查询的物品是 $modLevelString「${item.zhItemName}」\n$orderString")
    }

    /**
     * 如果找不到项目，则处理模糊搜索的功能
     *
     * @param itemNameKey 物品名称关键字
     */
    fun handleFuzzySearch(context: Context, itemNameKey: String) {
        val fuzzyList = mutableSetOf<String>()
        itemNameKey.forEach { char ->
            wfRivenService.superFuzzyQuery(char.toString())
                ?.forEach { it?.zhName?.let { name -> fuzzyList.add(name) } }
        }

        if (fuzzyList.isNotEmpty()) {
            otherUtil.findMatchingStrings(itemNameKey, fuzzyList.toList()).let {
                context.sendMsg("未找到该物品,也许你想找的是:[${it.joinToString(", ")}]")
            }
        } else {
            context.sendMsg("未找到任何匹配项。")
        }
    }

    /**
     * 处理参数并分离正负词条的功能
     *
     * @param parameters 参数
     * @param positiveStats 正词条
     * @param negativeStat 负词条
     */
    private fun processParameters(
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
    private fun createQueryParams(
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
    private fun createLichQueryParams(
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
    private fun timeAgo(timeStr: String): String {
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
    fun formatAuctionData(rivenJson: JsonNode, itemZhName: String, reRollTimes: Int? = null): Boolean {
        val orders = rivenJson["payload"]["auctions"]
        val allowedStatuses = setOf("online", "ingame", "offline")
        val polaritySymbols = mapOf("madurai" to "r", "vazarin" to "Δ", "naramon" to "一")

        val rivenOrderList = orders.asSequence()
            // 筛选游戏状态为 在线 和 游戏中 的信息
            .filter {
                it["owner"]["status"].textValue() in allowedStatuses
                if (reRollTimes != null) it["item"]["re_rolls"].intValue() == reRollTimes else true
            }
            .take(5)
            .map { order ->
                WfMarketVo.RivenOrderInfo(
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
                        val attr = WfMarketVo.Attributes(
                            value = attribute["value"].doubleValue(),
                            positive = attribute["positive"].booleanValue(),
                            urlName = wfRivenService.turnUrlNameToKeyByRiven(attribute["url_name"].textValue())
                        )
                        if (attr.positive) positive.add(attr)
                        else negative.add(attr)
                    }
                }
            }.toList()

        if (rivenOrderList.isEmpty()) return false

        WfMarket.rivenOrderList = WfMarketVo.RivenOrderList(
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
        val week: Int?
        var startTime: String?
        fun copyWithNewStartTime(newStartTime: String?): Week
    }

    data class OrdinaryWeek(
        override val week: Int? = null,
        val items: List<String>? = null,
        override var startTime: String? = null
    ) : Week {
        override fun copyWithNewStartTime(newStartTime: String?): OrdinaryWeek {
            return copy(startTime = newStartTime)
        }
    }

    data class SteelItem(val name: String? = null, val riven: Double? = null)
    data class SteelWeek(
        override val week: Int? = null,
        val items: List<SteelItem>? = null,
        override var startTime: String? = null
    ) : Week {
        override fun copyWithNewStartTime(newStartTime: String?): SteelWeek {
            return copy(startTime = newStartTime)
        }
    }

    data class Data(val ordinary: List<OrdinaryWeek>? = null, val steel: List<SteelWeek>? = null)


    private val dateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME

    fun <T : Week> findCurrentWeek(weeks: List<T>?, currentDate: LocalDateTime): T? {
        return weeks!!
            .sortedBy { it.startTime }
            .asSequence() // 使用序列，以便更高效地处理过滤和获取最后一项
            .filter { week ->
                week.startTime?.let { startTime ->
                    val startDateTime = OffsetDateTime.parse(startTime, dateTimeFormatter).toLocalDateTime()
                    startDateTime.isBefore(currentDate)
                } ?: true
            }
            .lastOrNull()
    }

    fun updateWeeks(data: Data, currentDate: LocalDateTime): Data {
        // 获取 steel 列表中最晚的开始时间及其索引
        val (maxSteelIndex, maxSteelStartTime) = findLatestWeekAndIndex(data.steel!!)
        // 获取 ordinary 列表中最晚的开始时间及其索引
        val (maxOrdinaryIndex, maxOrdinaryStartTime) = findLatestWeekAndIndex(data.ordinary!!)

        // 更新数据中的 steel 和 ordinary 列表
        return data.copy(
            steel = updateWeekTimes(data.steel, maxSteelStartTime, maxSteelIndex, currentDate),
            ordinary = updateWeekTimes(data.ordinary, maxOrdinaryStartTime, maxOrdinaryIndex, currentDate)
        )
    }

    // 辅助函数：查找列表中最晚的开始时间及其对应的索引
    private fun findLatestWeekAndIndex(weeks: List<Week>): Pair<Int, LocalDateTime> {
        return weeks.withIndex().maxByOrNull { (_, week) ->
            // 解析 startTime，并转换为 LocalDateTime，若为空则使用 LocalDateTime.MIN
            week.startTime?.let { startTime ->
                OffsetDateTime.parse(startTime, dateTimeFormatter).toLocalDateTime()
            } ?: LocalDateTime.MIN
        }?.let { (index, week) ->
            index to (week.startTime?.let { startTime ->
                OffsetDateTime.parse(startTime, dateTimeFormatter).toLocalDateTime()
            } ?: LocalDateTime.MIN)
        } ?: (0 to LocalDateTime.MIN)
    }

    // 更新每周时间的函数，根据当前时间和最晚时间进行调整
    private fun <T : Week> updateWeekTimes(
        weeks: List<T>,
        maxTime: LocalDateTime?, // 传入的最晚时间
        maxIndex: Int, // 对应的索引
        currentDate: LocalDateTime // 当前时间，用于计算时间差
    ): List<T> {
        if (maxTime == null) return weeks // 如果最晚时间为空，返回原始列表

        val daysSinceMax = Duration.between(maxTime, currentDate).toDays().toInt() // 计算从最晚时间到当前时间的天数
        if (daysSinceMax < 0) return weeks // 如果天数为负，不需要更新，直接返回原始列表

        val weekCount = weeks.size
        // 一次性计算需要更新的所有周数
        val weeksToUpdate = daysSinceMax / 7 // 计算总共需要推进的周数
        val offsetDays = daysSinceMax % 7 // 计算剩余的天数

        // 当前时间所在的周索引
        val nowWeekIndex = (maxIndex + weeksToUpdate) % weekCount

        return weeks.mapIndexed { i, week ->
            // 计算每个周的时间偏移量，确保每个周的时间一次性推进所有周数
            val offsetWeeks = i - nowWeekIndex
            val totalWeeksOffset = weeksToUpdate + offsetWeeks // 一次性推进所有周数
            val updatedDateTime = maxTime.plusWeeks(totalWeeksOffset.toLong()).plusDays(offsetDays.toLong())

            // 更新 startTime 并返回新的 week 对象
            week.startTime = formatDateTime(updatedDateTime)
            week
        }
    }

    // 格式化 LocalDateTime 为字符串
    private fun formatDateTime(dateTime: LocalDateTime): String {
        val zonedDateTime = dateTime.atZone(ZoneId.systemDefault()) // 转换为 ZonedDateTime
        return zonedDateTime.toOffsetDateTime().format(dateTimeFormatter) // 转换为 OffsetDateTime 并格式化
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


    fun findSpiralsCurrentTime(
        wfWeathers: List<WfUtilVo.WfWeather>,
        currentTime: LocalDateTime
    ): WfUtilVo.WfWeather? {
        return wfWeathers
            .filter { OffsetDateTime.parse(it.startTime, dateTimeFormatter).toLocalDateTime().isBefore(currentTime) }
            .maxByOrNull { OffsetDateTime.parse(it.startTime, dateTimeFormatter).toLocalDateTime() }
    }


    fun findLatestWeatherAndIndex(weathers: List<WfUtilVo.WfWeather>): LocalDateTime {
        return weathers.maxOfOrNull { OffsetDateTime.parse(it.startTime, dateTimeFormatter).toLocalDateTime() }
            ?: LocalDateTime.MIN
    }


    fun updateWeathers(spiralData: WfUtilVo.SpiralsData, currentTime: LocalDateTime): WfUtilVo.SpiralsData {
        val maxStartTime = findLatestWeatherAndIndex(spiralData.wfWeather)
        spiralData.wfWeather = updateWeatherStartTimes(spiralData.wfWeather, maxStartTime, currentTime)
        return spiralData
    }

    fun updateWeatherStartTimes(
        weatherData: List<WfUtilVo.WfWeather>,
        maxTime: LocalDateTime?,
        currentTime: LocalDateTime
    ): List<WfUtilVo.WfWeather> {
        if (maxTime == null) return weatherData

        // hours <= 1 说明仍在当前轮次内，不需要更新
        val hours = Duration.between(maxTime, currentTime).toHours()
        if (hours <= 1) return weatherData

        val weatherCount = weatherData.size
        // 当前时间所在位置下标
        val nowTimeIndex = (hours / 2 - 1) % weatherCount

        return weatherData.mapIndexed { i, weather ->
            // 计算时间偏移量，i - nowTimeIndex 可以是负值，偏移量是相对的
            val offset = (i - nowTimeIndex) * 2L
            weather.startTime = formatDateTime(maxTime.plusHours((if (hours % 2 != 0L) hours - 1 else hours) + offset))
            weather
        }
    }

    fun formatDuration(duration: Duration): String {
        return when {
            duration.toHours() > 0 -> "${duration.toHours()} 小时 ${duration.toMinutesPart()} 分钟 ${duration.toSecondsPart()} 秒"
            duration.toMinutes() > 0 -> "${duration.toMinutes()} 分钟 ${duration.toSecondsPart()} 秒"
            else -> "${duration.toSecondsPart()} 秒"
        }
    }

    fun getNpcLists(
        weatherData: WfUtilVo.SpiralsData,
        stateId: Int
    ): Pair<MutableList<Map<String, String>>, MutableList<Map<String, String>>> {
        val npcList = mutableListOf<Map<String, String>>()
        val excludeNpcList = mutableListOf<Map<String, String>>()

        weatherData.places.forEach { place ->
            place.npc?.forEach { npc ->
                if (npc.excludeIds.contains(stateId)) excludeNpcList.add(mapOf(npc.name to place.name))
                else npcList.add(mapOf(npc.name to place.name))
            }
        }

        return Pair(npcList, excludeNpcList)
    }

    fun getPlaceLists(weatherData: WfUtilVo.SpiralsData, stateId: Int): Pair<MutableList<String>, MutableList<String>> {
        val excludePlaceList = mutableListOf<String>()
        val noExcludePlaceList = mutableListOf<String>()

        weatherData.excludePlaces.forEach { place ->
            if (place.excludeIds.contains(stateId)) excludePlaceList.add(place.name)
            else noExcludePlaceList.add(place.name)
        }

        return Pair(excludePlaceList, noExcludePlaceList)
    }

    // 获取几个平原的状态
    fun getStatus(
        url: String,
        stateMap: Map<String, String>? = null
    ): WfStatusVo.WordStatus {
        val statusJson = HttpUtil.doGetJson(url, params = mapOf("language" to "zh"))
        val activation = statusJson["activation"].textValue().toEastEightTimeZone()
        val expiry = statusJson["expiry"].textValue().toEastEightTimeZone()
        val timeLeft = statusJson["timeLeft"].textValue().replaceTime()
        val state = statusJson["state"].textValue()

        val displayState = stateMap?.get(state) ?: state
        return WfStatusVo.WordStatus(
            displayState = displayState,
            activation = activation,
            expiry = expiry,
            timeLeft = timeLeft
        )
    }

    // 计算时间差并格式化为字符串
    fun formatTimeDifference(nowTime: LocalDateTime, endTime: LocalDateTime): String {
        val timeDifference = StringBuilder()

        // 计算各个时间单位
        val units = listOf(
            ChronoUnit.MONTHS to "个月",
            ChronoUnit.DAYS to "天",
            ChronoUnit.HOURS to "小时",
            ChronoUnit.MINUTES to "分",
            ChronoUnit.SECONDS to "秒",
        )

        var tempTime = nowTime

        for ((unit, unitName) in units) {
            val amount = unit.between(tempTime, endTime)
            if (amount > 0) {
                timeDifference.append("$amount$unitName")
                tempTime = tempTime.plus(amount, unit) // 更新临时时间
            }
        }

        return timeDifference.toString()
    }

    fun formatTimeBySecond(seconds: Long): String {
        val timeDifference = StringBuilder()

        val days = seconds / (24 * 60 * 60)
        var remainingSeconds = seconds % (24 * 60 * 60)

        val hours = remainingSeconds / (60 * 60)
        remainingSeconds %= (60 * 60)

        val minutes = remainingSeconds / 60
        val secondsLeft = remainingSeconds % 60

        if (days > 0) timeDifference.append("${days}天")
        if (hours > 0) timeDifference.append("${hours}小时")
        if (minutes > 0) timeDifference.append("${minutes}分钟")
        timeDifference.append("${secondsLeft}秒")

        return timeDifference.toString()
    }
}