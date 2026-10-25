package bot.wuliang.market.history

import bot.wuliang.market.MarketProperties
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.ZoneOffset

/** 保存 PC 排行榜的当前发布信息及上一期日期。 */
@Service
class MarketTradePublicationService(
    private val redis: StringRedisTemplate,
    private val objectMapper: ObjectMapper,
    private val properties: MarketProperties,
) {
    private val key get() = "${properties.redisKeyPrefix}:publication:pc"

    private fun read(): JsonNode? = redis.opsForValue().get(key)?.let(objectMapper::readTree)

    fun current(): MarketTradePublication? = read()?.let { node ->
        MarketTradePublication(
            LocalDate.parse(node.path("tradeDay").asText()), node.path("publishedAt").asLong(),
            node.path("itemCount").asLong(), node.path("recordCount").asLong(),
        ).also {
            check(it.tradeDay.isBefore(LocalDate.now(ZoneOffset.UTC)) && it.itemCount > 0 && it.recordCount >= it.itemCount) {
                "排行榜发布标记无效"
            }
        }
    }

    fun previousDay(): LocalDate? =
        read()?.get("previousDay")?.takeIf(JsonNode::isTextual)?.asText()?.let(LocalDate::parse)

    /** 成交数据校验事务提交后调用，校验同步锁并原子更新发布信息及上一期日期。 */
    fun publish(publication: MarketTradePublication, lockKey: String, owner: String) {
        val payload = objectMapper.createObjectNode().apply {
            put("tradeDay", publication.tradeDay.toString())
            put("publishedAt", publication.publishedAt)
            put("itemCount", publication.itemCount)
            put("recordCount", publication.recordCount)
        }.toString()
        val result = redis.execute(PUBLISH, listOf(lockKey, key), owner, publication.tradeDay.toString(), payload)
        check(result == 1L) { "排行榜发布锁已失效或已存在更新日期" }
    }

    companion object {
        private val PUBLISH = DefaultRedisScript(
            """
            if redis.call('GET', KEYS[1]) ~= ARGV[1] then return -1 end
            local next = cjson.decode(ARGV[3])
            local old = redis.call('GET', KEYS[2])
            if old then
                local previous = cjson.decode(old)
                if previous.tradeDay > ARGV[2] then return 0 end
                if previous.tradeDay < ARGV[2] then
                    next.previousDay = previous.tradeDay
                else
                    next.previousDay = previous.previousDay
                end
            end
            redis.call('SET', KEYS[2], cjson.encode(next))
            return 1
            """.trimIndent(),
            Long::class.javaObjectType,
        )
    }
}