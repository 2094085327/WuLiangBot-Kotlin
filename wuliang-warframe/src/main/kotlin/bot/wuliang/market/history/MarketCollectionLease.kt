package bot.wuliang.market.history

import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Component
import java.time.Duration

/** 采集互斥锁的续租与释放均校验持有者。 */
@Component
class MarketCollectionLease(private val redis: StringRedisTemplate) {
    fun acquire(key: String, owner: String): Boolean =
        redis.opsForValue().setIfAbsent(key, owner, Duration.ofSeconds(90)) == true

    fun renew(key: String, owner: String): Boolean = redis.execute(RENEW, listOf(key), owner) == 1L

    fun isOwner(key: String, owner: String): Boolean = redis.opsForValue().get(key) == owner

    fun release(key: String, owner: String) {
        redis.execute(RELEASE, listOf(key), owner)
    }

    fun nextRoundAt(key: String): Long = redis.opsForValue().get("$key:next")?.toLongOrNull() ?: 0L

    /** 校验所有者与共享冷却；逐商品恢复由统计日水位负责。 */
    fun beginRound(key: String, owner: String, now: Long): Boolean = redis.execute(
        BEGIN, listOf(key, "$key:next"), owner, now.toString(),
    ) == 1L

    fun finishRound(key: String, owner: String, nextAt: Long) {
        check(redis.execute(FINISH, listOf(key, "$key:next"), owner, nextAt.toString()) == 1L) {
            "WM 采集锁已失效，不能提交下一轮时间"
        }
    }

    fun isSourcePaused(key: String, source: String): Boolean = redis.hasKey("$key:paused:$source")

    fun pauseSource(key: String, source: String, duration: Duration) {
        redis.opsForValue().set("$key:paused:$source", "1", duration)
    }

    companion object {
        private val BEGIN = DefaultRedisScript(
            """
            if redis.call('GET', KEYS[1]) ~= ARGV[1] then return 0 end
            if tonumber(redis.call('GET', KEYS[2]) or '0') > tonumber(ARGV[2]) then return 0 end
            return 1
            """.trimIndent(),
            Long::class.javaObjectType,
        )
        private val FINISH = DefaultRedisScript(
            """
            if redis.call('GET', KEYS[1]) ~= ARGV[1] then return 0 end
            redis.call('SET', KEYS[2], ARGV[2])
            return 1
            """.trimIndent(),
            Long::class.javaObjectType,
        )
        private val RENEW = DefaultRedisScript(
            "if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('PEXPIRE', KEYS[1], 90000) else return 0 end",
            Long::class.javaObjectType,
        )
        private val RELEASE = DefaultRedisScript(
            "if redis.call('GET', KEYS[1]) == ARGV[1] then return redis.call('DEL', KEYS[1]) else return 0 end",
            Long::class.javaObjectType,
        )
    }
}