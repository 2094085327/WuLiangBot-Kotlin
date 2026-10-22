package bot.wuliang.market.history

import bot.wuliang.market.MarketProperties
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.ZoneOffset

data class MarketTradePublication(
    val tradeDay: LocalDate,
    val publishedAt: Long,
    val totalItems: Long,
    val processedItems: Long,
    val recovered: Boolean = false,
) {
    val pendingItems: Long get() = totalItems - processedItems
    val complete: Boolean get() = pendingItems == 0L
}

/** 发布元数据计入 Redis；丢失时依据同步表的已发布批次事实恢复。 */
@Service
class MarketTradePublicationService(
    private val redis: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
    private val properties: MarketProperties,
    private val trades: MarketDailyTradeStore,
    private val lease: MarketCollectionLease,
) {
    private val key get() = "${properties.redisKeyPrefix}:publication:v2:${properties.statistics.platform}:${MarketDailyTradeAverage.SOURCE}"

    fun current(): MarketTradePublication? {
        redis.opsForValue().get(key)?.let { return decode(it) }
        val checkpoint = trades.publicationCheckpoint() ?: return null
        val recovered = MarketTradePublication(
            checkpoint.tradeDay, System.currentTimeMillis(),
            checkpoint.totalItems, checkpoint.processedItems, recovered = true
        )
        // SET NX 防止恢复请求覆盖同时完成的新发布。不设 TTL。
        redis.opsForValue().setIfAbsent(key, encode(recovered))
        return decode(checkNotNull(redis.opsForValue().get(key)) { "WM发布标记恢复期间丢失，暂不切榜或清理数据" })
    }

    fun publish(day: LocalDate, lockKey: String, owner: String): MarketTradePublication? {
        check(lease.isOwner(lockKey, owner)) { "WM发布时采集锁已失效" }
        val progress = trades.checkpointPublication(day) ?: return null
        val publication =
            MarketTradePublication(day, System.currentTimeMillis(), progress.totalItems, progress.processedItems)
        val result = redis.execute(PUBLISH, listOf(lockKey, key), owner, day.toString(), encode(publication))
        check(result >= 0) { "WM发布时采集锁已失效，未更新Redis标记" }
        return if (result == 1L) publication else current()
    }

    private fun encode(value: MarketTradePublication): String = objectMapper.createObjectNode().apply {
        put("tradeDay", value.tradeDay.toString())
        put("publishedAt", value.publishedAt)
        put("totalItems", value.totalItems)
        put("processedItems", value.processedItems)
        put("pendingItems", value.pendingItems)
        put("complete", value.complete)
        put("recovered", value.recovered)
    }.toString()

    private fun decode(value: String): MarketTradePublication {
        val node = objectMapper.readTree(value)
        val result = MarketTradePublication(
            LocalDate.parse(node.path("tradeDay").asText()),
            node.path("publishedAt").asLong(), node.path("totalItems").asLong(), node.path("processedItems").asLong(),
            node.path("recovered").asBoolean(false)
        )
        check(
            result.tradeDay.isBefore(LocalDate.now(ZoneOffset.UTC)) && result.totalItems > 0 &&
                    result.processedItems in 0..result.totalItems && result.processedItems * 100 >= result.totalItems * 99
        ) {
            "WM发布元数据无效，暂不切榜或清理数据"
        }
        return result
    }

    companion object {
        private val PUBLISH = DefaultRedisScript(
            """
            if redis.call('GET', KEYS[1]) ~= ARGV[1] then return -1 end
            local old = redis.call('GET', KEYS[2])
            if old and cjson.decode(old).tradeDay > ARGV[2] then return 0 end
            redis.call('SET', KEYS[2], ARGV[3])
            return 1
            """.trimIndent(),
            Long::class.javaObjectType,
        )
    }
}