package bot.wuliang.market

import bot.wuliang.config.WARFRAME_MARKET_ITEMS_ORDERS_V2
import bot.wuliang.httpUtil.HttpUtil
import org.springframework.stereotype.Service

/** 查询最低卖价；优先读取精选订单，缺项时回退到可见卖单。 */
@Service
class MarketPriceService {

    /**
     * 优先读取精选卖单首项价格，缺项时回退查询可见卖单中的最低价格。
     *
     * @param key 物品的 Market slug。
     * @return 最低白金价格；没有卖单时返回零。
     */
    fun minimumSellPrice(key: String): Int {
        val headers: MutableMap<String, Any> = mutableMapOf(
            "accept" to "application/json",
            "language" to "en",
            "platform" to "pc",
            "crossplay" to "false"
        )
        val topJson = HttpUtil.doGetJson(
            url = "$WARFRAME_MARKET_ITEMS_ORDERS_V2/$key/top",
            headers = headers
        )
        val topOrders = topJson["data"]["sell"]
            ?.takeIf { it.isArray }
            ?.toList()
            ?: emptyList()
        if (topOrders.isNotEmpty()) {
            return topOrders.first()["platinum"].intValue()
        }

        val json = HttpUtil.doGetJson(
            url = "$WARFRAME_MARKET_ITEMS_ORDERS_V2/$key",
            headers = headers
        )
        val fallbackData = json["data"]
        val fallbackNode = when {
            fallbackData.isArray -> fallbackData
            fallbackData["sell"]?.isArray == true -> fallbackData["sell"]
            else -> null
        }
        val fallbackOrders = fallbackNode
            ?.filter {
                it["type"].textValue() == "sell" &&
                        it["visible"].asBoolean(false)
            }
            ?: emptyList()
        if (fallbackOrders.isEmpty()) {
            return 0
        }

        val minimalOrder = fallbackOrders.minByOrNull { it["platinum"].intValue() } ?: return 0
        val price = minimalOrder["platinum"].intValue()
        return price
    }
}