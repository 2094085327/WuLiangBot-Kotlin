package bot.wuliang.market

import bot.wuliang.adapter.context.ExecutionContext
import bot.wuliang.config.WARFRAME_MARKET_ITEMS_ORDERS_V2
import bot.wuliang.config.WfMarketConfig.WF_MARKET_CACHE_KEY
import bot.wuliang.entity.WfMarketItemEntity
import bot.wuliang.entity.vo.WfMarketVo
import bot.wuliang.httpUtil.HttpUtil
import bot.wuliang.jacksonUtil.JacksonUtil
import bot.wuliang.redis.RedisService
import bot.wuliang.utils.paginate
import com.fasterxml.jackson.databind.JsonNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

/** 查询市场订单并生成指令文本；保留分页、等级过滤和短期订单缓存策略。 */
@Service
class MarketOrderService(
    private val redisService: RedisService,
) {
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

    /** 按等级和页码生成订单消息，缺少精选订单时回退到普通卖单。 */
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
     * 在 IO 调度器中读取订单缓存或请求 Market，再转换为内部订单列表。
     *
     * @param item 需要查询订单的物品。
     * @param requestedRank 指定模组等级，为 null 时不传等级过滤参数。
     * @param top 是否查询精选订单接口。
     */
    private suspend fun fetchMarketOrders(
        item: WfMarketItemEntity,
        requestedRank: Int?,
        top: Boolean,
    ): List<MarketOrder> = withContext(Dispatchers.IO) {
        val headers = mutableMapOf<String, Any>(
            "accept" to "application/json",
            "language" to "en",
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

    /** 查询订单；遇到不支持指定等级的接口错误时发送提示并返回 null，其他错误继续抛出。 */
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

    /** 读取卖单价格、数量、等级、可见性和卖家状态，转换为排序与分页使用的内部模型。 */
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

    /** 按模组等级与展示策略筛选卖单，并生成等级说明；需要过滤时保留可见且在线的卖单。 */
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

    /** 根据精选查询、回退状态和指定页码生成订单分页及下一页信息。 */
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

    /** 将物品名称、等级说明、分页订单与后续查询提示组合为机器人文本。 */
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

    /** 替换卖家名称中的句点，避免机器人消息中的名称被识别为链接。 */
    private fun escapeMarketUserName(name: String): String = name.replace(".", "ׅ")
}