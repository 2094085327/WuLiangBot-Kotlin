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
        get() = listOf("requests", "background", "waiting", "cooldown", "contract-search")
            .map { "${properties.redisKeyPrefix}:$it" }

    /** 阻塞当前 IO 线程直至取得额度；Redis 不可用时停止派发。 */
    fun acquire(priority: MarketRequestPriority, contractSearch: Boolean = false, maxWaitMillis: Long? = null) {
        val ticket = UUID.randomUUID().toString()
        val interactive = priority == MarketRequestPriority.INTERACTIVE
        val deadline = maxWaitMillis?.let { System.nanoTime() + java.util.concurrent.TimeUnit.MILLISECONDS.toNanos(it) }
        try {
            while (true) {
                if (Thread.currentThread().isInterrupted) throw InterruptedException("Market 请求已取消")
                check(deadline == null || System.nanoTime() < deadline) { "等待 Market 请求额度超时" }
                val wait = redis.execute(
                    ACQUIRE, keys, ticket, if (interactive) "1" else "0",
                    properties.globalRequestsPerSecond.toString(), properties.backgroundRequestsPerSecond.toString(),
                    if (contractSearch) "1" else "0", properties.contractSearchRequestsPerMinute.toString(),
                )
                if (wait == 0L) return
                Thread.sleep(wait.coerceIn(10, 250))
            }
        } finally {
            if (interactive) redis.opsForZSet().remove(keys[2], ticket)
        }
    }

    /** 限流响应共同退避；跨请求、跨实例累计，半小时未触发后重新从30秒开始。 */
    fun cooldown(millis: Long): Long = requireNotNull(
        redis.execute(COOLDOWN, listOf(keys[3], "${properties.redisKeyPrefix}:rate-limit-count"),
            millis.coerceAtLeast(0).toString()),
    )

    companion object {
        private val ACQUIRE = DefaultRedisScript(
            """
            local t = redis.call('TIME')
            local now = t[1] * 1000 + math.floor(t[2] / 1000)
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', now - 1000)
            redis.call('ZREMRANGEBYSCORE', KEYS[2], '-inf', now - 1000)
            redis.call('ZREMRANGEBYSCORE', KEYS[3], '-inf', now)
            redis.call('ZREMRANGEBYSCORE', KEYS[5], '-inf', now - 60000)
            if ARGV[2] == '1' then
                redis.call('ZADD', KEYS[3], now + 60000, ARGV[1])
                redis.call('PEXPIRE', KEYS[3], 61000)
            end
            local cooldown = redis.call('PTTL', KEYS[4])
            if cooldown > 0 then return cooldown end
            if ARGV[2] == '0' and redis.call('ZCARD', KEYS[3]) > 0 then return 50 end
            local function budget(key, maximum, window)
                local last = redis.call('ZREVRANGE', key, 0, 0, 'WITHSCORES')
                local wait = 0
                if #last > 0 then wait = math.max(0, tonumber(last[2]) + math.ceil(window / maximum) - now) end
                if redis.call('ZCARD', key) >= maximum then
                    local first = redis.call('ZRANGE', key, 0, 0, 'WITHSCORES')
                    wait = math.max(wait, tonumber(first[2]) + window + 1 - now)
                end
                return wait
            end
            local wait = budget(KEYS[1], tonumber(ARGV[3]), 1000)
            if ARGV[2] == '0' then wait = math.max(wait, budget(KEYS[2], tonumber(ARGV[4]), 1000)) end
            if ARGV[5] == '1' then wait = math.max(wait, budget(KEYS[5], tonumber(ARGV[6]), 60000)) end
            if wait > 0 then return wait end
            if ARGV[5] == '1' then
                redis.call('ZADD', KEYS[5], now, ARGV[1])
                redis.call('PEXPIRE', KEYS[5], 61000)
            end
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
            local count = redis.call('INCR', KEYS[2])
            redis.call('PEXPIRE', KEYS[2], 1800000)
            local backoff = math.min(1800000, 30000 * math.pow(2, math.min(count - 1, 6)))
            local delay = math.max(tonumber(ARGV[1]), backoff, redis.call('PTTL', KEYS[1]))
            if redis.call('PTTL', KEYS[1]) < delay then
                redis.call('PSETEX', KEYS[1], delay, '1')
            end
            return delay
            """.trimIndent(),
            Long::class.javaObjectType,
        )
    }
}