package bot.wuliang.utils

import bot.wuliang.botLog.logUtil.LoggerUtils.logError
import bot.wuliang.config.*
import bot.wuliang.config.WfMarketConfig.WF_MARKET_CACHE_KEY
import bot.wuliang.controller.WfMarketController
import bot.wuliang.entity.WfMarketItemEntity
import bot.wuliang.entity.WfRivenEntity
import bot.wuliang.entity.vo.WfMarketVo
import bot.wuliang.entity.vo.WfStatusVo
import bot.wuliang.entity.vo.WfUtilVo
import bot.wuliang.httpUtil.HttpUtil
import bot.wuliang.httpUtil.ProxyUtil
import bot.wuliang.moudles.Info
import bot.wuliang.otherUtil.OtherUtil
import bot.wuliang.redis.RedisService
import bot.wuliang.service.WfMarketItemService
import bot.wuliang.service.WfRivenService
import bot.wuliang.utils.TimeUtils.replaceTime
import bot.wuliang.utils.WfUtil.WfUtilObject.toEastEightTimeZone
import com.fasterxml.jackson.databind.JsonNode
import com.github.houbb.opencc4j.util.ZhConverterUtil
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import java.io.File
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

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

    private val dateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME


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

    fun getLanguageValue(key: String): String? {
        return redisService.getValueTyped<Info>(
            "${WF_MARKET_CACHE_KEY}Languages:${key.lowercase()}"
        )?.value
    }

    fun getLanguageDesc(key: String): String? {
        return redisService.getValueTyped<Info>(
            "${WF_MARKET_CACHE_KEY}Languages:${key.lowercase()}"
        )?.desc
    }

    /**
     * 解析紫卡参数
     *
     * @param params 参数字符串
     * @return URL参数
     */
    fun parseRivenParams(params: String): String {
        if (params.isEmpty()) return ""

        val parts = params.trim().lowercase().split("\\s+".toRegex())
        val urlParams = mutableListOf<String>()

        // 支持的武器类型
        val weaponTypes = mapOf(
            "步枪" to "Rifle Riven Mod",
            "手枪" to "Pistol Riven Mod",
            "近战" to "Melee Riven Mod",
            "霰弹枪" to "Shotgun Riven Mod",
            "组合枪" to "Kitgun Riven Mod",
            "zaw" to "Zaw Riven Mod",
            "archgun" to "Archgun Riven Mod"
        )

        // 排序方向
        val sortDirections = mapOf(
            "正序" to "asc",
            "倒序" to "desc"
        )

        var type: String? = null
        var sort: String? = null
        var rerolled: String? = null

        for (part in parts) {
            // 检查武器类型
            weaponTypes.forEach { (key, value) ->
                if (part.contains(key, ignoreCase = true)) {
                    type = value
                }
            }

            // 检查排序方向
            sortDirections.forEach { (key, value) ->
                if (part.contains(key, ignoreCase = true)) {
                    sort = value
                }
            }

            // 检查是否已洗
            if (part.contains("0洗", ignoreCase = true) || part.contains("未洗", ignoreCase = true)) {
                rerolled = "false"
            } else if (part.contains("非0洗", ignoreCase = true) || part.contains("已洗", ignoreCase = true)) {
                rerolled = "true"
            }
        }

        // 构建URL参数
        type?.let { urlParams.add("type=$it") }
        sort?.let { urlParams.add("sort=$it") }
        rerolled?.let { urlParams.add("rerolled=$it") }

        return if (urlParams.isNotEmpty()) {
            "?" + urlParams.joinToString("&")
        } else {
            ""
        }
    }

    fun getMarketItems(): List<WfMarketItemEntity> {
        val json = HttpUtil.doGetJson(url = WARFRAME_MARKET_ITEMS_V2, headers = LANGUAGE_ZH_HANS)
        val items = json["data"]

        return items.map { item ->
            val i18n = item["i18n"]

            WfMarketItemEntity(
                id = item["id"].textValue(),
                urlName = item["slug"].textValue(),
                zhName = i18n["zh-hans"]["name"]?.textValue(),
                enName = i18n["en"]["name"].textValue(),
                ducats = item["ducats"]?.intValue()
            )
        }
    }

    fun getRivenItems(): List<WfRivenEntity> {
        val json = HttpUtil.doGetJson(url = WARFRAME_MARKET_RIVEN_ITEMS_V2, headers = LANGUAGE_ZH_HANS)
        val items = json["data"]

        return items.map { item ->
            val i18n = item["i18n"]
            WfRivenEntity(
                id = item["id"].textValue(),
                urlName = item["slug"].textValue(),
                zhName = i18n["zh-hans"]["name"]?.textValue(),
                enName = i18n["en"]["name"].textValue(),
                rGroup = item["group"].textValue(),
                reqMasteryRank = item["reqMasteryRank"].floatValue(),
                rivenType = item["rivenType"].textValue(),
                disposition = item["disposition"].floatValue(),
                attributesBool = 0
            )
        }
    }

    fun getRivenAttributes(): List<WfRivenEntity> {
        val json = HttpUtil.doGetJson(url = WARFRAME_MARKET_RIVEN_ATTRIBUTES_V2, headers = LANGUAGE_ZH_HANS)
        val items = json["data"]

        return items.map { item ->
            WfRivenEntity(
                id = item["id"].textValue(),
                urlName = item["slug"].textValue(),
                zhName = item["i18n"]["zh-hans"]["name"].textValue(),
                enName = item["i18n"]["en"]["name"].textValue(),
                rGroup = item["group"].textValue(),
                attributesBool = 1
            )
        }
    }
}