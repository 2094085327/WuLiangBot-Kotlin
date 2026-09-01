package bot.wuliang.utils

import bot.wuliang.adapter.context.ExecutionContext
import bot.wuliang.botLog.logUtil.LoggerUtils.logError
import bot.wuliang.config.*
import bot.wuliang.config.WfMarketConfig.WF_MARKET_CACHE_KEY
import bot.wuliang.config.WfMarketConfig.WF_VOIDTRADER_KEY
import bot.wuliang.entity.WfMarketItemEntity
import bot.wuliang.entity.WfRivenAttributeEntity
import bot.wuliang.entity.WfRivenEntity
import bot.wuliang.entity.vo.WfMarketVo
import bot.wuliang.entity.vo.WfStatusVo
import bot.wuliang.entity.vo.WfUtilVo
import bot.wuliang.httpUtil.HttpUtil
import bot.wuliang.httpUtil.ProxyUtil
import bot.wuliang.imageProcess.WebImgUtil
import bot.wuliang.jacksonUtil.JacksonUtil
import bot.wuliang.moudles.Info
import bot.wuliang.moudles.VoidTrader
import bot.wuliang.otherUtil.OtherUtil
import bot.wuliang.redis.RedisService
import bot.wuliang.riven.RivenGroups
import bot.wuliang.riven.RivenQueryCriteria
import bot.wuliang.service.WfMarketItemService
import bot.wuliang.service.WfRivenService
import bot.wuliang.tencentCos.CosFileServiceImpl
import bot.wuliang.utils.TimeUtils.replaceTime
import bot.wuliang.utils.WfUtil.WfUtilObject.toEastEightTimeZone
import com.fasterxml.jackson.databind.JsonNode
import com.github.houbb.opencc4j.util.ZhConverterUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import java.io.File
import java.time.*
import java.time.format.DateTimeFormatter
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

    private data class MarketOrder(
        val hasRank: Boolean,
        val rank: Int?,
        val type: String,
        val visible: Boolean,
        val platinum: Int,
        val quantity: Int,
        val inGameName: String,
        val status: String,
    )

    private data class MarketOrderSelection(
        val orders: List<MarketOrder>,
        val rankQuery: String?,
    )

    private data class MarketOrderPage(
        val orders: List<MarketOrder>,
        val pageInfo: String,
        val nextPage: Int?,
    )

    @Autowired
    private lateinit var wfMarketItemService: WfMarketItemService

    @Autowired
    private lateinit var wfRivenService: WfRivenService

    @Autowired
    private lateinit var redisService: RedisService

    @Autowired
    private lateinit var proxyUtil: ProxyUtil

    @Autowired
    private lateinit var txCosService: CosFileServiceImpl

    @Autowired
    private lateinit var webImgUtil: WebImgUtil

    @Qualifier("otherUtil")
    private lateinit var otherUtil: OtherUtil

    /**
     * 查询并发送 Warframe Market 物品订单信息。
     *
     * @param context 消息执行上下文
     * @param item 物品信息
     * @param modLevel 模组等级或“满级”
     * @param page 页码；为空时优先查询精选订单
     */
    suspend fun sendMarketItemInfo(
        context: ExecutionContext,
        item: WfMarketItemEntity,
        modLevel: String? = null,
        page: Int? = null
    ) {
        val topRequested = page == null && modLevel != "满级"
        val requestedRank = modLevel?.toIntOrNull()
        var usingFallback = false
        var orders = fetchMarketOrdersOrNotify(context, item, requestedRank, topRequested) ?: return
        if (topRequested && orders.isEmpty()) {
            usingFallback = true
            orders = fetchMarketOrdersOrNotify(context, item, requestedRank, top = false) ?: return
        }

        val selection = selectMarketOrders(
            orders = orders,
            modLevel = modLevel,
            applyFilters = !topRequested || usingFallback,
        )
        val marketPage = paginateMarketOrders(
            orders = selection.orders,
            requestedPage = page,
            topRequested = topRequested,
            usingFallback = usingFallback,
        )
        context.sender.sendText(
            renderMarketItemMessage(item, selection.rankQuery, marketPage, page != null || usingFallback)
        )
    }

    /**
     * 从缓存或 Warframe Market 获取订单，并将 JSON 订单转换为内部模型。
     *
     * 网络请求和同步 Redis 操作在 IO 调度器中执行，避免阻塞消息处理线程。
     *
     * @param item 物品信息
     * @param requestedRank 请求的具体模组等级
     * @param top 是否请求精选订单
     * @return 解析后的订单列表
     */
    private suspend fun fetchMarketOrders(
        item: WfMarketItemEntity,
        requestedRank: Int?,
        top: Boolean,
    ): List<MarketOrder> = withContext(Dispatchers.IO) {
        val headers = mutableMapOf<String, Any>(
            "accept" to "application/json",
            "language" to "zh-hans",
            "platform" to "pc",
        )
        val suffix = if (top) "/top" else ""
        val url = "$WARFRAME_MARKET_ITEMS_ORDERS_V2/${item.urlName}$suffix"
        val cacheKey = "${WF_MARKET_CACHE_KEY}orders:$url:rank=${requestedRank ?: "all"}"
        val cachedOrders = redisService.getValueTyped<String>(cacheKey)
        val orderNodes = if (cachedOrders != null) {
            JacksonUtil.readTree(cachedOrders).toList()
        } else {
            val json = HttpUtil.doGetJson(
                url = url,
                headers = headers,
                params = requestedRank?.let { mapOf("rank" to it) },
            )
            val data = json["data"]
            val orderNode = when {
                data.isArray -> data
                data["sell"]?.isArray == true -> data["sell"]
                else -> JacksonUtil.readTree("[]")
            }
            redisService.setValueWithExpiry(
                cacheKey,
                JacksonUtil.toJsonString(orderNode),
                1L,
                TimeUnit.MINUTES
            )
            orderNode.toList()
        }
        orderNodes.map(::parseMarketOrder)
    }

    /**
     * 获取订单并处理不支持指定模组等级的 API 错误。
     *
     * @param context 消息执行上下文
     * @param item 物品信息
     * @param requestedRank 请求的具体模组等级
     * @param top 是否请求精选订单
     * @return 订单列表；等级不支持时发送提示并返回 null
     */
    private suspend fun fetchMarketOrdersOrNotify(
        context: ExecutionContext,
        item: WfMarketItemEntity,
        requestedRank: Int?,
        top: Boolean,
    ): List<MarketOrder>? {
        return try {
            fetchMarketOrders(item, requestedRank, top)
        } catch (e: HttpUtil.HttpException) {
            if (requestedRank != null &&
                e.statusCode == 400 &&
                e.responseBody.contains("app.field.unsupportedValue")
            ) {
                context.sender.sendText("「${item.zhName}」不支持${requestedRank}级，请输入该物品允许的等级")
                null
            } else {
                throw e
            }
        }
    }

    /**
     * 将 API 返回的订单 JSON 转换为内部订单模型。
     *
     * @param node 单条订单 JSON
     * @return 内部订单模型
     */
    private fun parseMarketOrder(node: JsonNode): MarketOrder {
        val user = node["user"]
        return MarketOrder(
            hasRank = node.has("rank"),
            rank = node["rank"].takeIf { node.has("rank") }?.intValue(),
            type = node["type"].textValue().orEmpty(),
            visible = node["visible"].asBoolean(false),
            platinum = node["platinum"].intValue(),
            quantity = node["quantity"].intValue(),
            inGameName = user["ingameName"].textValue().orEmpty(),
            status = user["status"].textValue().orEmpty(),
        )
    }

    /**
     * 根据等级、可见性和用户在线状态筛选订单。
     *
     * “满级”使用完整订单集计算最大等级，再筛选可见订单，避免离线高等级订单影响查询结果。
     *
     * @param orders 原始订单列表
     * @param modLevel 用户输入的等级
     * @param applyFilters 是否应用普通订单筛选；精选订单由 API 负责排序和筛选
     * @return 筛选后的订单及实际生效的等级查询
     */
    private fun selectMarketOrders(
        orders: List<MarketOrder>,
        modLevel: String?,
        applyFilters: Boolean,
    ): MarketOrderSelection {
        val supportsRank = orders.any { it.hasRank }
        val rankQuery = modLevel.takeIf { supportsRank }
        val requestedRank = modLevel?.toIntOrNull()
        val maxModRank = if (rankQuery == "满级") {
            orders.asSequence()
                .filter { it.hasRank }
                .mapNotNull { it.rank }
                .maxOrNull()
        } else {
            requestedRank
        }

        if (!applyFilters) {
            return MarketOrderSelection(orders, rankQuery)
        }

        val filteredOrders = orders.asSequence()
            .filter { it.visible }
            .filter { order ->
                when {
                    rankQuery == null -> true
                    requestedRank != null -> !order.hasRank || order.rank == requestedRank
                    else -> order.hasRank && order.rank == maxModRank
                }
            }
            .filter { it.type == "sell" && it.status in setOf("online", "ingame") }
            .sortedBy { it.platinum }
            .toList()

        return MarketOrderSelection(filteredOrders, rankQuery)
    }

    /**
     * 对订单执行普通分页，或将精选订单包装为第一页结果。
     *
     * @param orders 待分页订单
     * @param requestedPage 用户请求的页码
     * @param topRequested 是否请求精选订单
     * @param usingFallback 是否已经从精选接口回退到普通接口
     * @return 分页结果和下一页信息
     */
    private fun paginateMarketOrders(
        orders: List<MarketOrder>,
        requestedPage: Int?,
        topRequested: Boolean,
        usingFallback: Boolean,
    ): MarketOrderPage {
        if (topRequested && !usingFallback) {
            return MarketOrderPage(
                orders = orders,
                pageInfo = "\n精选订单",
                nextPage = 2,
            )
        }

        val page = orders.paginate(requestedPage ?: 1)

        return MarketOrderPage(
            orders = page.items,
            pageInfo = "\n页码：${page.currentPage}/${page.totalPages}",
            nextPage = page.nextPage,
        )
    }

    /**
     * 将订单分页结果渲染为机器人消息文本。
     *
     * @param item 物品信息
     * @param rankQuery 实际生效的等级查询
     * @param page 分页结果
     * @param showVisibleOrderMessage 是否显示“可见订单”提示
     * @return 待发送的消息文本
     */
    private fun renderMarketItemMessage(
        item: WfMarketItemEntity,
        rankQuery: String?,
        page: MarketOrderPage,
        showVisibleOrderMessage: Boolean,
    ): String {
        val filteredOrders = page.orders.map {
            WfMarketVo.OrderInfo(
                platinum = it.platinum,
                quantity = it.quantity,
                inGameName = it.inGameName,
                userStatus = when (it.status) {
                    "online" -> "在线中"
                    "ingame" -> "游戏中"
                    else -> "离线"
                }
            )
        }
        val orderString = if (filteredOrders.isEmpty()) {
            if (showVisibleOrderMessage) {
                "当前没有可见的玩家出售${item.zhName}"
            } else {
                "当前没有任何在线的玩家出售${item.zhName}"
            }
        } else {
            filteredOrders.joinToString("\n") {
                "| ${escapeMarketUserName(it.inGameName)} \n" +
                        "| 价格: ${it.platinum} 数量: ${it.quantity} 状态：${it.userStatus}\n"
            } + "\n/w ${escapeMarketUserName(filteredOrders.first().inGameName)} " +
                    "Hi! I want to buy: \"${item.enName}\" for " +
                    "${filteredOrders.first().platinum} platinum.(wf.m WuLiang-Bot)"
        }
        val modLevelString = when {
            rankQuery == "满级" -> "满级"
            rankQuery != null -> "${rankQuery}级"
            else -> ""
        }
        val nextPageMessage = page.nextPage?.let {
            "\n\n使用'wm ${item.zhName} -$it' 查看下一页"
        }.orEmpty()

        return "你查询的物品是 ${modLevelString}「${item.zhName}」${page.pageInfo}\n" +
                orderString + nextPageMessage
    }

    /**
     * 替换游戏内名称中的点号，避免消息平台将名称解析为其他格式。
     *
     * @param name 游戏内名称
     * @return 可用于消息文本的名称
     */
    private fun escapeMarketUserName(name: String): String = name.replace(".", "ׅ")

    /**
     * 如果找不到项目，则处理模糊搜索的功能
     *
     * @param itemNameKey 物品名称关键字
     */
    suspend fun handleFuzzySearch(context: ExecutionContext, itemNameKey: String) {
        val fuzzyList = mutableSetOf<String>()
        itemNameKey.forEach { char ->
            wfRivenService.superFuzzyQuery(char.toString())
                ?.forEach { it?.zhName?.let { name -> fuzzyList.add(name) } }
        }

        if (fuzzyList.isNotEmpty()) {
            otherUtil.findMatchingStrings(itemNameKey, fuzzyList.toList()).let {
                context.sender.sendText("未找到该物品,也许你想找的是:${it.joinToString(", ")}")
            }
        } else {
            context.sender.sendText("未找到任何匹配项")
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

    fun getRivenAuctionsJson(criteria: RivenQueryCriteria): JsonNode? = try {
        HttpUtil.doGetJson(WARFRAME_MARKET_RIVEN_AUCTIONS, params = criteria.toMarketParams())
    } catch (e: Exception) {
        logError("WM紫卡查询错误:$e")
        null
    }

    fun getLichAuctionsJson(
        element: String? = null,
        ephemera: String? = null,
        itemEntityUrlName: String,
        lichType: String? = null
    ): JsonNode? {
        val ephemeraBoolean = ephemera?.contains("有")
        val queryParams = createLichQueryParams(itemEntityUrlName, element = element, ephemera = ephemeraBoolean)

        return try {
            when (lichType) {
                RivenGroups.LICH -> HttpUtil.doGetJson(WARFRAME_MARKET_LICH_AUCTIONS, params = queryParams)
                else -> HttpUtil.doGetJson(WARFRAME_MARKET_SISTER_AUCTIONS, params = queryParams)
            }
        } catch (e: Exception) {
            logError("WM玄骸查询错误:$e")
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

    fun getMarketCollectionVersions(): Map<String, String> {
        val collections = HttpUtil.doGetJson(
            url = WARFRAME_MARKET_VERSIONS_V2,
            headers = LANGUAGE_ZH_HANS
        )["data"]["collections"]

        return listOf("items", "rivens", "liches", "sisters").associateWith { collection ->
            collections[collection]?.textValue()?.takeIf { it.isNotBlank() }
                ?: error("Warframe Market API 版本校验缺失: $collection")
        }
    }

    private fun JsonNode.localizedName(language: String): String? {
        return this["i18n"]?.get(language)?.get("name")?.textValue()
    }

    fun getMarketItems(): List<WfMarketItemEntity> {
        val json = HttpUtil.doGetJson(url = WARFRAME_MARKET_ITEMS_V2, headers = LANGUAGE_ZH_HANS)
        val items = json["data"]

        return items.map { item ->
            val tags = item["tags"]
                ?.takeIf { it.isArray }
                ?.mapNotNull { it.textValue() }
                ?: emptyList()

            WfMarketItemEntity(
                id = item["id"].textValue(),
                urlName = item["slug"].textValue(),
                gameRef = item["gameRef"]?.textValue(),
                tags = JacksonUtil.toJsonString(tags),
                zhName = item.localizedName("zh-hans"),
                enName = item.localizedName("en"),
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
                enName = i18n["en"]["name"]?.textValue(),
                rGroup = item["group"]?.textValue(),
                reqMasteryRank = item["reqMasteryRank"]?.floatValue(),
                rivenType = item["rivenType"]?.textValue(),
                disposition = item["disposition"]?.floatValue(),
            )
        }
    }

    private fun getV2WeaponItems(url: String, group: String): List<WfRivenEntity> {
        val items = HttpUtil.doGetJson(url = url, headers = LANGUAGE_ZH_HANS)["data"]
        return items.map { item ->
            WfRivenEntity(
                id = item["id"].textValue(),
                urlName = item["slug"].textValue(),
                zhName = item.localizedName("zh-hans"),
                enName = item.localizedName("en"),
                rGroup = group,
                reqMasteryRank = item["reqMasteryRank"]?.floatValue(),
            )
        }
    }

    fun getLichItems(): List<WfRivenEntity> =
        getV2WeaponItems(WARFRAME_MARKET_LICH_WEAPONS_V2, RivenGroups.LICH)

    fun getSisterItems(): List<WfRivenEntity> =
        getV2WeaponItems(WARFRAME_MARKET_SISTER_WEAPONS_V2, RivenGroups.SISTER)

    fun getRivenAttributes(): List<WfRivenAttributeEntity> {
        val items = HttpUtil.doGetJson(
            url = WARFRAME_MARKET_RIVEN_ATTRIBUTES_V2,
            headers = LANGUAGE_ZH_HANS
        )["data"]
        return items.map(WfRivenAttributeParser::parse)
    }

    /**
     * 将年日转换为 "MM月DD日" 格式（1999年，非闰年）
     */
    fun dayOfYearToDate(day: Int): String {
        val monthDays = intArrayOf(31, 28, 31, 30, 31, 30, 31, 31, 30, 31, 30, 31)
        var remaining = day
        for (i in monthDays.indices) {
            if (remaining <= monthDays[i]) {
                return String.format("%02d月%02d日", i + 1, remaining)
            }
            remaining -= monthDays[i]
        }
        return ""
    }

    /**
     * 获取并缓存本周周常图片，图片名固定为本周一日期与奸商状态
     */
    fun getWeeklyImgUrl(): String {
        // 周常每周一刷新，按周一日期与奸商状态生成图片名，缓存在COS中一周
        val weeklyKey = TimeUtils.getFirstDayOfWeek()
            .atZone(ZoneId.of("UTC"))
            .toLocalDate()
            .toString()
        // 奸商激活状态写入缓存key，抵达后第一次生成周常图会带上库存
        val voidTraderList = redisService.getValueTyped<List<VoidTrader>>(WF_VOIDTRADER_KEY)
        val voidTraderState = when {
            voidTraderList?.any { it.isActive == true } == true -> "active"
            voidTraderList.isNullOrEmpty() -> "none"
            else -> "upcoming"
        }
        txCosService.ensureLifecycleRule("weekly-", 7)

        val imgData = WebImgUtil.ImgData(
            url = "http://${webImgUtil.frontendAddress}/weekly",
            imgName = "weekly-$weeklyKey-$voidTraderState",
            element = "#app",
            waitElement = ".warframeWeekly"
        )

        return webImgUtil.getImgUrl(imgData)
    }
}