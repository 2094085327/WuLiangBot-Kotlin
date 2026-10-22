package bot.wuliang.market

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Component
import java.util.*

enum class MarketRequestPriority { INTERACTIVE, BACKGROUND }

/** Market 请求限制器 */
@Component
class MarketRequestLimiter(
    private val redis: StringRedisTemplate,
    private val properties: MarketProperties,
) {
    private val keys
        get() = listOf("requests", "background", "waiting", "cooldown")
            .map { "${properties.redisKeyPrefix}:$it" }

    /** 阻塞当前 IO 线程直至取得额度；Redis 不可用时停止派发。 */
    fun acquire(priority: MarketRequestPriority) {
        val ticket = UUID.randomUUID().toString()
        val interactive = priority == MarketRequestPriority.INTERACTIVE
        try {
            while (true) {
                if (Thread.currentThread().isInterrupted) throw InterruptedException("Market 请求已取消")
                val wait = redis.execute(
                    ACQUIRE, keys, ticket, if (interactive) "1" else "0",
                    properties.globalRequestsPerSecond.toString(), properties.backgroundRequestsPerSecond.toString(),
                )
                if (wait == 0L) return
                Thread.sleep(wait.coerceIn(10, 250))
            }
        } finally {
            if (interactive) redis.opsForZSet().remove(keys[2], ticket)
        }
    }

    /** 限流响应共同退避。 */
    fun cooldown(millis: Long) {
        redis.execute(COOLDOWN, listOf(keys[3]), millis.coerceIn(1_000, 300_000).toString())
    }

    companion object {
        private val ACQUIRE = DefaultRedisScript(
            """
            local t = redis.call('TIME')
            local now = t[1] * 1000 + math.floor(t[2] / 1000)
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', now - 1000)
            redis.call('ZREMRANGEBYSCORE', KEYS[2], '-inf', now - 1000)
            redis.call('ZREMRANGEBYSCORE', KEYS[3], '-inf', now)
            if ARGV[2] == '1' then
                redis.call('ZADD', KEYS[3], now + 60000, ARGV[1])
                redis.call('PEXPIRE', KEYS[3], 61000)
            end
            local cooldown = redis.call('PTTL', KEYS[4])
            if cooldown > 0 then return cooldown end
            if ARGV[2] == '0' and redis.call('ZCARD', KEYS[3]) > 0 then return 50 end
            local function budget(key, maximum)
                local last = redis.call('ZREVRANGE', key, 0, 0, 'WITHSCORES')
                local wait = 0
                if #last > 0 then wait = math.max(0, tonumber(last[2]) + math.ceil(1000 / maximum) - now) end
                if redis.call('ZCARD', key) >= maximum then
                    local first = redis.call('ZRANGE', key, 0, 0, 'WITHSCORES')
                    wait = math.max(wait, tonumber(first[2]) + 1001 - now)
                end
                return wait
            end
            local wait = budget(KEYS[1], tonumber(ARGV[3]))
            if ARGV[2] == '0' then wait = math.max(wait, budget(KEYS[2], tonumber(ARGV[4]))) end
            if wait > 0 then return wait end
            redis.call('ZADD', KEYS[1], now, ARGV[1])
            redis.call('PEXPIRE', KEYS[1], 2000)
            if ARGV[2] == '0' then
                redis.call('ZADD', KEYS[2], now, ARGV[1])
                redis.call('PEXPIRE', KEYS[2], 2000)
            else
                redis.call('ZREM', KEYS[3], ARGV[1])
            end
            return 0
            """.trimIndent(),
            Long::class.javaObjectType,
        )
        private val COOLDOWN = DefaultRedisScript(
            """
            if redis.call('PTTL', KEYS[1]) < tonumber(ARGV[1]) then
                redis.call('PSETEX', KEYS[1], ARGV[1], '1')
            end
            return 1
            """.trimIndent(),
            Long::class.javaObjectType,
        )
    }
}