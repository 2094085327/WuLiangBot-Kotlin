package bot.wuliang.utils

import bot.wuliang.botLog.logUtil.LoggerUtils.logError
import bot.wuliang.botLog.logUtil.LoggerUtils.logInfo
import bot.wuliang.botUtil.BotUtils
import bot.wuliang.config.WARFRAME_MARKET_ITEMS
import bot.wuliang.config.WARFRAME_MARKET_LICH_AUCTIONS
import bot.wuliang.config.WARFRAME_MARKET_RIVEN_AUCTIONS
import bot.wuliang.config.WARFRAME_MARKET_SISTER_AUCTIONS
import bot.wuliang.controller.WfMarketController
import bot.wuliang.entity.WfMarketItemEntity
import bot.wuliang.entity.WfRivenEntity
import bot.wuliang.entity.vo.WfMarketVo
import bot.wuliang.entity.vo.WfStatusVo
import bot.wuliang.entity.vo.WfUtilVo
import bot.wuliang.httpUtil.HttpUtil
import bot.wuliang.httpUtil.ProxyManager
import bot.wuliang.httpUtil.ProxyUtil
import bot.wuliang.httpUtil.entity.ProxyInfo
import bot.wuliang.otherUtil.OtherUtil
import bot.wuliang.redis.RedisService
import bot.wuliang.service.WfMarketItemService
import bot.wuliang.service.WfRivenService
import bot.wuliang.utils.WfStatus.replaceTime
import bot.wuliang.utils.WfUtil.WfUtilObject.toEastEightTimeZone
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.JsonNode
import com.github.houbb.opencc4j.util.ZhConverterUtil
import kotlinx.coroutines.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.io.File
import java.net.InetSocketAddress
import java.net.Proxy
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.coroutines.coroutineContext
import kotlin.random.Random


/**
 * @description: Warframe 工具类
 * @author Nature Zero
 * @date 2024/6/4 上午9:45
 */
@Component
class WfUtil {

    @Autowired
    private lateinit var wfMarketItemService: WfMarketItemService

    @Autowired
    private lateinit var wfRivenService: WfRivenService

    @Autowired
    private lateinit var redisService: RedisService

    @Autowired
    private lateinit var proxyUtil: ProxyUtil

    @Autowired
    @Qualifier("otherUtil")
    private lateinit var otherUtil: OtherUtil

    /**
     * 发送物品信息
     *
     * @param item 物品
     * @param modLevel 模组等级
     */
    fun sendMarketItemInfo(context: BotUtils.Context, item: WfMarketItemEntity, modLevel: Any? = null) {
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
            "当前没有任何在线的玩家出售${item.zhName}"
        } else {
            filteredOrders.joinToString("\n") {
                "| ${it.inGameName.replace(".", "ׅ")} \n" + "| 价格: ${it.platinum} 数量: ${it.quantity}\n"
            } + "\n/w ${
                filteredOrders.first().inGameName.replace(
                    ".",
                    "ׅ"
                )
            } Hi! I want to buy: \"${item.enName}\" for ${filteredOrders.first().platinum} platinum.(wf.m WuLiang-Bot)"
        }

        val modLevelString = when {
            modLevel == "满级" -> "满级"
            modLevel != null -> "${modLevel}级"
            else -> ""
        }

        context.sendMsg("你查询的物品是 $modLevelString「${item.zhName}」\n$orderString")
    }

    /**
     * 如果找不到项目，则处理模糊搜索的功能
     *
     * @param itemNameKey 物品名称关键字
     */
    fun handleFuzzySearch(context: BotUtils.Context, itemNameKey: String) {
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
        negativeStat: String?,
        otherParams: Map<String, String>? = null
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
            otherParams?.let {
                putAll(it)
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
        val polaritySymbols = mapOf("madurai" to "r", "vazarin" to "Δ", "naramon" to "一")

        val rivenOrderList = orders.asSequence()
            // 筛选游戏状态为 在线 和 游戏中 的信息
            .filter {
                if (reRollTimes != null) it["item"]["re_rolls"].intValue() == reRollTimes else true
            }
            // 按照 status 排序，ingame -> online -> offline
            .sortedBy {
                when (it["owner"]["status"].textValue()) {
                    "ingame" -> 0
                    "online" -> 1
                    "offline" -> 2
                    else -> 3
                }
            }
            .take(5)
            .map { order ->
                val status = order["owner"]["status"].textValue()

                WfMarketVo.RivenOrderInfo(
                    user = order["owner"]["ingame_name"].textValue(),
                    userStatus = if (status == "online") "游戏在线" else if (status == "ingame") "游戏中" else "离线",
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

        WfMarketController.WfMarket.rivenOrderList = WfMarketVo.RivenOrderList(
            itemName = itemZhName,
            orderList = rivenOrderList
        )

        return true
    }

    /**
     * 用于紫卡均价的拍卖数据格式化，计算均值和标准差，并使用2σ原则过滤异常值，返回正态分布结果
     *
     * @param rivenJson 紫卡Json数据
     * @return 此物品正态分布价值
     */
    fun formatAuctionDataForNormalDist(rivenJson: JsonNode?): Double {
        if (rivenJson == null) return 0.0
        val orders = rivenJson["payload"]?.get("auctions") ?: return 0.0

        val prices = orders.asSequence()
            .map { it["starting_price"].doubleValue() }
            .filter { it > 0 } // 过滤掉0或负价格
            .toList()

        if (prices.size < 3) return prices.average()

        // 计算均值和标准差
        val mean = prices.average()
        val variance = prices.map { (it - mean) * (it - mean) }.average()
        val stdDev = kotlin.math.sqrt(variance)

        // 使用2σ原则过滤异常值（保留95%的正常数据）
        val lowerBound = mean - 2 * stdDev
        val upperBound = mean + 2 * stdDev

        val filteredPrices = prices.filter { it in lowerBound..upperBound }

        // 如果过滤后数据太少，放宽到3σ
        return if (filteredPrices.size >= prices.size * 0.5) {
            filteredPrices.average()
        } else {
            val lowerBound3 = mean - 3 * stdDev
            val upperBound3 = mean + 3 * stdDev
            prices.filter { it in lowerBound3..upperBound3 }.average()
        }
    }

    /**
     * 获取拍卖数据
     *
     * @param parameterList 紫卡参数列表
     * @param element 武器元素
     * @param ephemera 是否有幻纹
     * @param itemEntityUrlName 物品Url名称
     * @param auctionType 拍卖类型
     * @param lichType 玄骸类型
     * @return Json数据
     */
    fun getAuctionsJson(
        parameterList: List<String>? = null,
        element: String? = null,
        ephemera: String? = null,
        itemEntityUrlName: String,
        auctionType: String,
        lichType: String? = null
    ): JsonNode? {
        val positiveStats = mutableListOf<String>()
        val negativeStat: String? = null

        val queryParams = when (auctionType) {
            "riven" -> {
                parameterList?.let { processParameters(it.drop(1), positiveStats, negativeStat) }
                createQueryParams(itemEntityUrlName, positiveStats, negativeStat)
            }

            "lich" -> {
                val ephemeraBoolean = ephemera?.contains("有")
                createLichQueryParams(itemEntityUrlName, element = element, ephemera = ephemeraBoolean)
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
     * 获取紫卡拍卖数据
     *
     * @param itemEntityUrlName 物品Url名称
     * @return Json数据
     */
    fun getAuctionsJsonForRiven(
        itemEntityUrlName: String,
        headers: MutableMap<String, Any>? = null,
        proxy: Proxy? = null
    ): JsonNode? {
        val queryParams = createQueryParams(itemEntityUrlName, mutableListOf(), null, mapOf("re_rolls_max" to "0"))
        return HttpUtil.doGetJson(
            WARFRAME_MARKET_RIVEN_AUCTIONS,
            params = queryParams,
            headers = headers,
            proxy = proxy
        )
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

    data class SteelItem(
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("riven") val riven: Double? = null,
        @JsonProperty("url_name") val urlName: String? = null
    )

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
        // 每周7天
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
            duration.toDays() > 0 -> "${duration.toDays()} 天 ${duration.toHoursPart()} 小时 ${duration.toMinutesPart()} 分钟 ${duration.toSecondsPart()} 秒"
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
        val statusJson = HttpUtil.doGetJson(url, params = mapOf("language" to "zh"), proxy = proxyUtil.randomProxy())
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


    /**
     * 定时更新紫卡数据
     *
     */
    @Scheduled(cron = "0 0 11 * * ?")
    fun getAllRivenPriceTiming() {
        updateRivenData()
    }


    /**
     * 更新紫卡排行数据
     */
    private fun updateRivenData() {

        // 确保代理已加载
        if (!redisService.hasKey("Wuliang:http:proxy")) {
            proxyUtil.proxyMain()
        }

        val dbRivenList = wfRivenService.selectAllRivenData()
        val redisKeys = redisService.getListKey("warframe:riven:*")

        val dbKeys = mutableSetOf<String>()
        val toUpdate = mutableListOf<WfRivenEntity>()
        val urlNameList = mutableListOf<String>()

        dbRivenList.forEach { data ->
            val urlName = data.urlName ?: return@forEach
            val key = "warframe:riven:${urlName}"

            dbKeys.add(key)
            urlNameList.add(urlName)

            if (!redisKeys.contains(key)) {
                toUpdate.add(data)
            }
        }

        if (toUpdate.isNotEmpty()) {
            logInfo("检测到 ${toUpdate.size} 条紫卡数据发生变更，正在更新缓存...")
            toUpdate.forEach { data ->
                redisService.setValue("warframe:riven:${data.urlName}", data)
            }
        }

        val toDelete = redisKeys.subtract(dbKeys)
        if (toDelete.isNotEmpty()) {
            logInfo("检测到 ${toDelete.size} 条废弃紫卡数据，正在清理缓存...")
            redisService.deleteKey(toDelete)
        }

        if (urlNameList.isNotEmpty()) {
            runBlocking {
                // 首先尝试获取新数据
                val results = getAllRiven(urlNameList)
                results?.let { cacheRivenAvg(it) }

                // 然后尝试重试失败的数据
                retryFailedItems(3)
                // 获取已生成紫卡均价缓存并缓存排序后的紫卡排行
                val riveAvgResult = redisService.getListKey("warframe:rivenAvg:*").mapNotNull { key ->
                    val parts = key.split(":")
                    if (parts.size >= 3) {
                        val itemKey = parts[2]
                        val value = redisService.getValue(key)?.toString()
                        if (value != null) itemKey to value else null
                    } else null
                }.associate { it }
                cacheRivenAvg(riveAvgResult)
                cacheSortedRivenRanking(riveAvgResult)
            }
        }

        System.gc()
    }


    /**
     * 获取全部武器紫卡的平均价格
     *
     * @param weaponList 所有武器的列表
     * @return Map<String, String>
     */
    fun getAllRiven(weaponList: List<String>?): Map<String, String>? = runBlocking {
        if (weaponList.isNullOrEmpty()) return@runBlocking null

        val proxyList = redisService.getValueTyped<List<ProxyInfo>>("Wuliang:http:proxy")
        val proxies = proxyList?.takeIf { it.isNotEmpty() }?.map { proxyInfo ->
            Proxy(Proxy.Type.SOCKS, InetSocketAddress(proxyInfo.ip, proxyInfo.port!!))
        } ?: return@runBlocking null

        val proxyManager = ProxyManager(proxies)
        val successfulResults = Collections.synchronizedMap(mutableMapOf<String, String>())
        val failedItems = Collections.synchronizedSet(mutableSetOf<String>())

        try {
            val jobs = weaponList.map { weapon ->
                async {
                    try {
                        val result = getAvgWithProxyManager(weapon, proxyManager)
                        result[weapon]?.let { value ->
                            successfulResults[weapon] = value
                        } ?: run {
                            failedItems.add(weapon)
                        }
                    } catch (e: Exception) {
                        failedItems.add(weapon)
                    }
                }
            }

            // 等待全部完成
            jobs.awaitAll()

        } finally {
            proxyManager.close()
        }

        if (failedItems.isNotEmpty()) {
            recordFailedItems(failedItems)
        }

        successfulResults
    }

    /**
     * 使用代理管理器的重试方法
     */
    suspend fun getAvgWithProxyManager(
        item: String,
        proxyManager: ProxyManager,
        retries: Int = 3
    ): Map<String, String> {
        var lastException: Exception? = null
        var currentProxy: Proxy? = null

        for (attempt in 1..retries) {
            try {
                // 检查协程是否已取消
                coroutineContext.ensureActive()

                // 获取代理
                currentProxy = proxyManager.acquireProxy()

                // 执行请求
                return withTimeout(60000L) {
                    delay(Random.nextLong(50, 200))
                    getAvgWithProxy(item, currentProxy!!)
                }
            } catch (e: Exception) {
                lastException = e
            } finally {
                // 确保释放代理
                currentProxy?.let { proxy ->
                    proxyManager.releaseProxy(proxy)
                }
                currentProxy = null
            }

            // 非最终重试时等待
            if (attempt < retries) {
                delay(1000L + Random.nextLong(0, 1000))
            }
        }

        throw lastException ?: Exception("$item: 未知错误")
    }

    /**
     * 使用代理获取单个物品价格
     */
    suspend fun getAvgWithProxy(item: String, proxy: Proxy): Map<String, String> {
        try {
            // 添加随机延迟
            delay(Random.nextLong(100, 300))

            val rivenJson = getAuctionsJsonForRiven(
                itemEntityUrlName = item,
                generateRandomHeaders(),
                proxy
            )

            val formatAuctionDataForAvg = formatAuctionDataForNormalDist(rivenJson)

            return mapOf(item to String.format("%.2f", formatAuctionDataForAvg))

        } catch (e: Exception) {
            throw e
        }
    }


    /**
     * 获取并重试失败的物品
     */
    private suspend fun retryFailedItems(maxRetries: Int = 3) {
        var remainingFailed = redisService.getValueTyped<Set<String>>("warframe:riven:failed") ?: emptySet()

        if (remainingFailed.isNotEmpty()) {
            for (attempt in 1..maxRetries) {
                val results = getAllRiven(remainingFailed.toList())
                if (results == null) {
                    delay(5000L) // 等待后继续重试
                    continue
                }

                // 异步缓存结果，不阻塞重试流程
                CoroutineScope(Dispatchers.IO).launch {
                    cacheRivenAvg(results)
                }

                remainingFailed = remainingFailed - results.keys
                if (remainingFailed.isEmpty()) break
                delay(10000L)
            }

            if (remainingFailed.isNotEmpty()) {
                redisService.setValueWithExpiry("warframe:riven:failed", remainingFailed, 3600L, TimeUnit.SECONDS)
            } else {
                redisService.deleteKey("warframe:riven:failed")
            }
        }
    }


    /**
     * 获取当前 灵化武器紫卡 的平均价格
     *
     * @param incarnonList 本周灵化武器列表
     * @return Map<String, String>
     */
    fun getIncarnonRiven(incarnonList: List<SteelWeek>?): Map<String, String>? = runBlocking {
        val items = incarnonList?.get(0)?.items ?: return@runBlocking null

        // 获取所有物品的 URL 名称
        val itemUrlNames = items.map { it.urlName!! }

        // 从 Redis 缓存中获取已存在的数据
        val cachedResults = itemUrlNames.associateWith { urlName ->
            redisService.getValueTyped<String>("warframe:rivenAvg:$urlName")
        }

        // 分离出缓存中存在和不存在的物品
        val cachedEntries = mutableMapOf<String, String>()
        val uncachedItems = mutableListOf<SteelItem>()

        for (item in items) {
            val urlName = item.urlName!!
            val cachedValue = cachedResults[urlName]
            if (cachedValue != null) {
                cachedEntries[urlName] = cachedValue
            } else {
                uncachedItems.add(item)
            }
        }

        val proxyList = redisService.getValueTyped<List<ProxyInfo>>("Wuliang:http:proxy")
        val proxies = proxyList?.takeIf { it.isNotEmpty() }?.map { proxyInfo ->
            Proxy(Proxy.Type.SOCKS, InetSocketAddress(proxyInfo.ip, proxyInfo.port!!))
        } ?: return@runBlocking null
        val proxyManager = ProxyManager(proxies)

        // 对未缓存的物品并发获取平均价格
        val uncachedResults = uncachedItems.map { item ->
            async(Dispatchers.Default) {
                getAvgWithProxyManager(item.urlName!!, proxyManager)
            }
        }.awaitAll().flatMap { it.entries }.associate { it.toPair() }

        // 合并缓存中的结果与新获取的结果
        (cachedEntries + uncachedResults).takeIf { it.isNotEmpty() }
    }

    /**
     * 记录失败的物品，用于后续重试
     */
    private fun recordFailedItems(failedItems: Set<String>) {
        val existingFailed = redisService.getValueTyped<Set<String>>("warframe:riven:failed") ?: emptySet()
        val allFailed = existingFailed + failedItems
        redisService.setValueWithExpiry("warframe:riven:failed", allFailed, 3600L, TimeUnit.SECONDS) // 1小时过期
        logInfo("记录了 ${failedItems.size} 个失败物品，总计 ${allFailed.size} 个待重试")
    }

    /**
     * 生成随机的请求头
     *
     * @return MutableMap<String, Any> 生成的请求头
     */
    fun generateRandomHeaders(): MutableMap<String, Any> {
        val cookie = "JWT=${generateRandomJWT()}"
        val userAgentRandom = "${
            Random.nextInt(
                90,
                150
            )
        }.${Random.nextInt(0, 100)}"
        val userAgent =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/${userAgentRandom}.0.0 Safari/537.36 Edg/${userAgentRandom}.0.0"
        return mutableMapOf(
            "Cookie" to cookie,
            "User-Agent" to userAgent
        )
    }

    /**
     * 生成随机的 JWT
     *
     * @return String 生成的随机 JWT
     */
    fun generateRandomJWT(): String {
        val secretKey = "your_secret_key"
        val issuer = "your_issuer"
        val audience = "your_audience"

        val currentTimeSeconds = System.currentTimeMillis() / 1000
        val expirationTime = currentTimeSeconds + 3600 // 1小时有效期

        val header = """{"alg":"HS256","typ":"JWT"}"""
        val payload = buildString {
            append("{")
            append("\"sub\":\"${UUID.randomUUID()}\",")
            append("\"iat\":$currentTimeSeconds,")
            append("\"exp\":$expirationTime,")
            append("\"iss\":\"$issuer\",")
            append("\"aud\":\"$audience\"")
            append("}")
        }

        try {
            val encodedHeader = Base64.getUrlEncoder().withoutPadding().encodeToString(header.toByteArray())
            val encodedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toByteArray())

            val signature = sign("$encodedHeader.$encodedPayload", secretKey)

            return "$encodedHeader.$encodedPayload.$signature"
        } catch (e: Exception) {
            logError("JWT生成失败: ${e.message}")
            throw RuntimeException("JWT生成失败", e)
        }
    }

    /**
     * 对数据进行签名
     *
     * @param data 待前面数据
     * @param secret 秘钥
     * @return String 签名后的数据
     */
    fun sign(data: String, secret: String): String {
        val hmacSha256 = Mac.getInstance("HmacSHA256")
        val secretKey = SecretKeySpec(secret.toByteArray(), "HmacSHA256")
        hmacSha256.init(secretKey)
        val signature = hmacSha256.doFinal(data.toByteArray())
        return Base64.getUrlEncoder().withoutPadding().encodeToString(signature)
    }

    /**
     * 缓存排序后的紫卡数据
     *
     */
    private fun cacheSortedRivenRanking(avgMap: Map<String, String>) {
        val sortedList = avgMap.mapNotNull { (key, value) ->
            val rivenEntity = redisService.getValueTyped<WfRivenEntity>("warframe:riven:$key")
            rivenEntity?.zhName?.let { name ->
                WfMarketVo.RivenRank(name, value.toDoubleOrNull() ?: 0.0)
            }
        }.sortedByDescending { it.value }

        // 缓存排序后的列表
        redisService.setValueWithExpiry("warframe:rivenRanking", sortedList, 25L, TimeUnit.HOURS)
    }

    /**
     *  缓存紫卡平均价格
     *
     * @param results 存入Redis的数据
     */
    private fun cacheRivenAvg(results: Map<String, Any>) {
        val now = ZonedDateTime.now(ZoneId.of("Asia/Shanghai"))
        val expiryInSeconds = Duration.between(now, now.plusDays(1).withHour(11).withMinute(55).withSecond(0)).seconds

        results.forEach { (key, value) ->
            redisService.setValueWithExpiry("warframe:rivenAvg:$key", value, expiryInSeconds, TimeUnit.SECONDS)
        }
    }

    /**
     * 根据物品名称获取物品数据
     *
     * @param key
     * @return WfLexiconEntity 查询到的物品数据
     */
    fun fetchItemEntity(key: String): WfMarketItemEntity? {
        val itemEntity = wfMarketItemService.selectItemByAccurateNature(key)
        if (itemEntity != null) {
            redisService.setValueWithExpiry("warframe:lexicon:$key", itemEntity, 30L, TimeUnit.DAYS)
            return itemEntity
        }

        val marketItemList = wfMarketItemService.getItemByFuzzyMatching(key)
        if (!marketItemList.isNullOrEmpty()) {
            val firstItemEntity = marketItemList.first()
            redisService.setValueWithExpiry("warframe:lexicon:$key", firstItemEntity, 30L, TimeUnit.DAYS)
            return firstItemEntity
        }

        return null
    }

    fun getNextRefreshTime(startDate: LocalDateTime, now: LocalDateTime, interval: Duration): LocalDateTime {
        val durationSinceStart = Duration.between(startDate, now)
        val fullCycles = durationSinceStart.toDays() / interval.toDays()
        val lastRefresh = startDate.plus(interval.multipliedBy(fullCycles))
        return if (lastRefresh.isBefore(now)) lastRefresh.plus(interval) else lastRefresh
    }

    /**
     * 转换json文件简繁
     */
    fun processJsonFilesZh(directoryPath: String) {
        val directory = File(directoryPath)
        if (!directory.exists() || !directory.isDirectory) {
            println("无效的目录路径")
            return
        }

        // 创建固定大小的线程池
        val executorService: ExecutorService = Executors.newFixedThreadPool(4)

        // 遍历目录中的所有 JSON 文件
        val files = directory.walkTopDown()
            .filter { it.isFile && it.extension.equals("json", ignoreCase = true) }
            .toList()

        // 为每个文件创建一个任务
        val futures = files.map { file ->
            CompletableFuture.runAsync({
                try {
                    // 读取文件内容
                    val original = file.readText(Charsets.UTF_8)

                    // 调用工具类进行中文转换
                    val result = ZhConverterUtil.toSimple(original)

                    // 将处理后的内容写回文件
                    file.writeText(result, Charsets.UTF_8)

                    println("已处理文件：${file.name}")
                } catch (e: Exception) {
                    System.err.println("处理文件 ${file.name} 时出错: ${e.message}")
                }
            }, executorService)
        }

        // 等待所有任务完成
        CompletableFuture.allOf(*futures.toTypedArray()).join()

        // 关闭线程池
        executorService.shutdown()
    }

    /**
     * 获取当前周的第一天（周一）的 UTC 时间
     */
    fun getFirstDayOfWeek(): Instant {
        val now = Instant.now()

        // 获取本周一的时间（UTC 00:00:00）
        return now
            .atOffset(ZoneOffset.UTC)
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .toInstant()
    }

    fun getLastDayOfWeek(): Instant {
        val firstDay = getFirstDayOfWeek().atOffset(ZoneOffset.UTC)
        return firstDay
            .plusDays(6)
            .withHour(23)
            .withMinute(59)
            .withSecond(59)
            .withNano(0)
            .toInstant()
    }

    fun getStartOfDay(): Instant {
        val now = Instant.now().atOffset(ZoneOffset.UTC)
        return now
            .withHour(0)
            .withMinute(0)
            .withSecond(0)
            .withNano(0)
            .toInstant()
    }

    fun getStartOfNextDay(): Instant {
        val now = Instant.now().atOffset(ZoneOffset.UTC)
        return now
            .plusDays(1) // 先将时间加一天
            .withHour(0) // 设置为当天的 00:00:00
            .withMinute(0)
            .withSecond(0)
            .withNano(0)
            .toInstant()
    }

    fun getEndOfDay(): Instant {
        val now = Instant.now().atOffset(ZoneOffset.UTC)
        return now
            .withHour(23)
            .withMinute(59)
            .withSecond(59)
            .withNano(0)
            .toInstant()
    }
}