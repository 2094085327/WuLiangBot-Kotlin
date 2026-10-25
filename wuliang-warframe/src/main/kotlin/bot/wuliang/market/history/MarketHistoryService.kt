package bot.wuliang.market.history

import bot.wuliang.config.WARFRAME_MARKET_ITEMS_STATISTICS
import bot.wuliang.mapper.WfMarketItemMapper
import bot.wuliang.market.MarketApiClient
import bot.wuliang.market.MarketProperties
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.time.Duration

data class MarketHistoryResult(
    val slug: String,
    val platform: String,
    val source: String,
    val window: String,
    val fetchedAt: Long,
    val points: JsonNode,
)

/** 历史仅在用户查询时获取；缓存 closed 两个窗口，保留上游实际规格，完全丢弃 live。 */
@Service
class MarketHistoryService(
    private val api: MarketApiClient,
    private val properties: MarketProperties,
    private val items: WfMarketItemMapper,
    private val redis: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
) {
    private val locks = Array(64) { Any() }

    /** 两个窗口来自同一次缓存快照，供趋势图与摘要共用。 */
    fun queryWindows(slug: String): Map<String, MarketHistoryResult> {
        require(slug.length in 1..128 && slug.matches(Regex("[a-z0-9_]+"))) { "商品 slug 无效。" }
        require(items.findIdBySlug(slug) != null) { "未找到该市场商品。" }
        val key = "${properties.redisKeyPrefix}:history:closed:$slug"
        // 同实例合并同一商品的并发查询；跨实例的请求仍受全局限流控制。
        val cached = synchronized(locks[(key.hashCode() and Int.MAX_VALUE) % locks.size]) {
            redis.opsForValue().get(key)?.let(objectMapper::readTree) ?: run {
                val response = api.getJson(
                    WARFRAME_MARKET_ITEMS_STATISTICS.format(slug),
                    headers = mapOf("Platform" to "pc", "Language" to "en", "Crossplay" to "false"),
                )
                val closed = response.path("payload").path("statistics_closed")
                val clean = objectMapper.createObjectNode()
                for (period in listOf("48hours", "90days")) {
                    val points = closed[period]
                    require(points != null && points.isArray) { "WM历史统计缺少 $period 数组" }
                    clean.set<JsonNode>(period, points)
                }
                objectMapper.createObjectNode().apply {
                    put("fetchedAt", System.currentTimeMillis())
                    set<JsonNode>("closed", clean)
                }.also {
                    redis.opsForValue().set(key, it.toString(), Duration.ofSeconds(properties.historyCacheSeconds))
                }
            }
        }
        return listOf("48hours", "90days").associateWith { window ->
            MarketHistoryResult(
                slug, "pc", "wm_closed", window,
                cached.path("fetchedAt").asLong(), cached.path("closed").get(window)
            )
        }
    }
}