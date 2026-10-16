package bot.wuliang.worldstate.service

import bot.wuliang.config.WarframeProperties
import bot.wuliang.config.WfMarketConfig.WF_VOIDTRADER_KEY
import bot.wuliang.moudles.VoidTrader
import bot.wuliang.moudles.VoidTraderItem
import bot.wuliang.redis.RedisService
import bot.wuliang.translation.PublicExportService
import bot.wuliang.worldstate.parser.TraderParser
import bot.wuliang.worldstate.presentation.TraderInventoryOrder
import bot.wuliang.worldstate.presentation.asInstant
import bot.wuliang.worldstate.presentation.remainingTime
import com.fasterxml.jackson.databind.JsonNode
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

/** 商人查询沿用原有缓存键和商品分组；到访后空库存只做短暂缓存以等待上游发布。 */
@Service
class TraderQueryService(
    private val redis: RedisService,
    private val translations: PublicExportService,
    properties: WarframeProperties,
) {
    private val retrySeconds = properties.worldState.retrySeconds

    /** 优先读取商人响应缓存；未命中时翻译并排序库存，待发布的空库存使用短缓存重试。 */
    fun getVoidTraders(records: JsonNode): List<VoidTrader> {
        translations.index()
        val cacheKey = translations.cacheKey(WF_VOIDTRADER_KEY)
        redis.getValueTyped<List<VoidTrader>>(cacheKey)?.let { return it }
        val now = System.currentTimeMillis()
        val states = TraderParser.parse(records, now)
        val values = states.map { trader ->
            VoidTrader(
                id = trader.id,
                activation = trader.activation.asInstant(),
                expiry = trader.expiry.asInstant(),
                eta = remainingTime(if (trader.active) trader.expiry else trader.activation, now),
                isActive = trader.active,
                node = translations.node(trader.node).name,
                inventory = TraderInventoryOrder.sort(trader.inventory.map { offer ->
                    VoidTraderItem(
                        item = translations.name(offer.item),
                        ducats = offer.primePrice ?: 0,
                        credits = offer.regularPrice ?: 0
                    )
                }.filter { !it.item.isNullOrBlank() }),
            )
        }
        val pending = states.isEmpty() || states.any { (it.active && it.inventory.isEmpty()) || it.expiry == null }
        val ttl = if (pending) retrySeconds else states.minOf { trader ->
            (if (trader.active) trader.expiry else trader.activation)?.let { ((it - now) / 1000).coerceAtLeast(1) }
                ?: retrySeconds
        }
        redis.setValueWithExpiry(cacheKey, values, ttl, TimeUnit.SECONDS)
        return values
    }
}