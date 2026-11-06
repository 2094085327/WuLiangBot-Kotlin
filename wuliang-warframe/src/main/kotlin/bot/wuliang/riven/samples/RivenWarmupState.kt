package bot.wuliang.riven.samples

import bot.wuliang.market.MarketProperties
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit

/** 自动采集的共享日额度、预热进度和同武器补采间隔，不保存待采集任务。 */
@Component
class RivenWarmupState(
    private val redis: StringRedisTemplate,
    private val market: MarketProperties,
    private val properties: RivenSampleProperties,
) {
    private val prefix get() = "${market.redisKeyPrefix}:riven:{warmup}"
    private fun dayKey(day: LocalDate) = "$prefix:day:$day"
    private fun field(weapon: String) = "weapon:$weapon"
    private fun analysisWeaponKey(weapon: String) = "${market.redisKeyPrefix}:riven:analysis-weapon:$weapon"
    fun dayEnd(day: LocalDate): Long = day.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    /** 首次使用直接初始化；已记录当天控制状态但日进度丢失时，暂停到次日。 */
    fun readyAfter(day: LocalDate): Long {
        return requireNotNull(
            redis.execute(
                READY, listOf("$prefix:control", dayKey(day)),
                day.toString(), dayEnd(day).toString(), (dayEnd(day) + TimeUnit.DAYS.toMillis(2)).toString()
            )
        )
    }

    fun progress(day: LocalDate): Map<String, String> =
        redis.opsForHash<String, String>().entries(dayKey(day))

    fun attempted(progress: Map<String, String>, weapon: String) = progress[field(weapon)] == "done"
    fun retryAfter(progress: Map<String, String>, weapon: String) = progress[field(weapon)]?.toLongOrNull() ?: 0L
    fun exhausted(progress: Map<String, String>): Boolean {
        val warmup = progress["requests"]?.toInt() ?: 0
        val analysis = progress["analysisRequests"]?.toInt() ?: 0
        return warmup >= properties.warmup.dailyLimit || warmup + analysis >= properties.dailyLimit
    }
    fun nextRequestAt(progress: Map<String, String>) = progress["next"]?.toLong() ?: 0L

    fun complete(day: LocalDate, weapon: String, until: Long) {
        redis.execute(
            COMPLETE, listOf(dayKey(day)), field(weapon),
            until.toString(), (dayEnd(day) + TimeUnit.DAYS.toMillis(2)).toString()
        )
    }

    fun defer(day: LocalDate, weapon: String, until: Long) {
        redis.execute(
            DEFER, listOf(dayKey(day)), field(weapon), until.toString(),
            (dayEnd(day) + TimeUnit.DAYS.toMillis(2)).toString()
        )
    }

    /** 发送前原子占用额度。失败与崩溃不返还；UTC 跨日后拒绝旧日请求。 */
    fun reserve(day: LocalDate, weapon: String): Boolean = redis.execute(
        RESERVE,
        listOf(dayKey(day), "$prefix:control"),
        field(weapon),
        day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli().toString(),
        dayEnd(day).toString(),
        properties.warmup.dailyLimit.toString(),
        TimeUnit.SECONDS.toMillis(properties.warmup.intervalSeconds).toString(),
        (dayEnd(day) + TimeUnit.DAYS.toMillis(2)).toString(),
        day.toString(), properties.dailyLimit.toString()
    ) == 1L

    /** 轻量预查；最终额度和武器、查询间隔仍在发送前由Lua原子检查。 */
    fun analysisReady(weapon: String, lastSuccessAt: Long, now: Long): Boolean {
        if (lastSuccessAt > now - TimeUnit.HOURS.toMillis(properties.analysis.refreshHours)) return false
        val day = LocalDate.now(ZoneOffset.UTC)
        if (readyAfter(day) > now) return false
        val progress = progress(day)
        val count = progress["analysisRequests"]?.toInt() ?: 0
        if (count >= properties.analysis.dailyLimit ||
            count + (progress["requests"]?.toInt() ?: 0) >= properties.dailyLimit) return false
        return !redis.hasKey(analysisWeaponKey(weapon))
    }

    fun reserveAnalysis(weapon: String, lastSuccessAt: Long): Boolean {
        val day = LocalDate.now(ZoneOffset.UTC)
        if (readyAfter(day) > System.currentTimeMillis()) return false
        return redis.execute(
            RESERVE_ANALYSIS,
            listOf(dayKey(day), "$prefix:control", analysisWeaponKey(weapon)),
            day.toString(), day.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli().toString(),
            dayEnd(day).toString(), properties.dailyLimit.toString(), properties.analysis.dailyLimit.toString(),
            TimeUnit.HOURS.toMillis(properties.analysis.refreshHours).toString(),
            TimeUnit.MINUTES.toMillis(properties.analysis.weaponIntervalMinutes).toString(), lastSuccessAt.toString(),
            (dayEnd(day) + TimeUnit.DAYS.toMillis(2)).toString()
        ) == 1L
    }

    companion object {
        private val READY = DefaultRedisScript(
            """
            local time = redis.call('TIME')
            local now = tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)
            if redis.call('EXISTS', KEYS[1]) == 0 then
                redis.call('HSET', KEYS[1], 'day', ARGV[1], 'after', 0)
                redis.call('HSETNX', KEYS[2], 'initialized', 1)
                redis.call('PEXPIREAT', KEYS[2], ARGV[3])
                return 0
            end
            local after = tonumber(redis.call('HGET', KEYS[1], 'after') or '0')
            if after > now then return after end
            if redis.call('HGET', KEYS[1], 'day') ~= ARGV[1] then
                redis.call('HSET', KEYS[1], 'day', ARGV[1], 'after', 0)
                redis.call('HSET', KEYS[2], 'initialized', 1)
                redis.call('PEXPIREAT', KEYS[2], ARGV[3])
            elseif redis.call('EXISTS', KEYS[2]) == 0 then
                redis.call('HSET', KEYS[1], 'after', ARGV[2])
                return tonumber(ARGV[2])
            end
            return 0
        """.trimIndent(), Long::class.javaObjectType
        )
        private val COMPLETE = DefaultRedisScript(
            """
            if redis.call('HEXISTS', KEYS[1], 'initialized') == 0 then return 0 end
            redis.call('HSET', KEYS[1], ARGV[1], ARGV[2])
            redis.call('PEXPIREAT', KEYS[1], ARGV[3])
            return 1
        """.trimIndent(), Long::class.javaObjectType
        )
        private val DEFER = DefaultRedisScript(
            """
            if redis.call('HEXISTS', KEYS[1], 'initialized') == 0 then return 0 end
            if redis.call('HGET', KEYS[1], ARGV[1]) == 'done' then return 0 end
            local previous = tonumber(redis.call('HGET', KEYS[1], ARGV[1]) or '0')
            redis.call('HSET', KEYS[1], ARGV[1], math.max(previous, tonumber(ARGV[2])))
            redis.call('PEXPIREAT', KEYS[1], ARGV[3])
            return 1
        """.trimIndent(), Long::class.javaObjectType
        )
        private val RESERVE = DefaultRedisScript(
            """
            local time = redis.call('TIME')
            local now = tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)
            if now < tonumber(ARGV[2]) or now >= tonumber(ARGV[3]) then return 0 end
            if redis.call('HGET', KEYS[2], 'day') ~= ARGV[7] then return 0 end
            if tonumber(redis.call('HGET', KEYS[2], 'after') or '0') > now then return 0 end
            if redis.call('HEXISTS', KEYS[1], 'initialized') == 0 then return 0 end
            local state = redis.call('HGET', KEYS[1], ARGV[1])
            if state == 'done' or tonumber(state or '0') > now then return 0 end
            local warmup = tonumber(redis.call('HGET', KEYS[1], 'requests') or '0')
            local analysis = tonumber(redis.call('HGET', KEYS[1], 'analysisRequests') or '0')
            if warmup >= tonumber(ARGV[4]) or warmup + analysis >= tonumber(ARGV[8]) then return 0 end
            if tonumber(redis.call('HGET', KEYS[1], 'next') or '0') > now then return 0 end
            redis.call('HINCRBY', KEYS[1], 'requests', 1)
            redis.call('HSET', KEYS[1], 'next', now + tonumber(ARGV[5]), ARGV[1], 'done')
            redis.call('PEXPIREAT', KEYS[1], ARGV[6])
            return 1
        """.trimIndent(), Long::class.javaObjectType
        )
        private val RESERVE_ANALYSIS = DefaultRedisScript(
            """
            local t = redis.call('TIME')
            local now = t[1] * 1000 + math.floor(t[2] / 1000)
            if now < tonumber(ARGV[2]) or now >= tonumber(ARGV[3]) then return 0 end
            if redis.call('HGET', KEYS[2], 'day') ~= ARGV[1] then return 0 end
            if tonumber(redis.call('HGET', KEYS[2], 'after') or '0') > now then return 0 end
            if redis.call('HEXISTS', KEYS[1], 'initialized') == 0 then return 0 end
            local warmup = tonumber(redis.call('HGET', KEYS[1], 'requests') or '0')
            local analysis = tonumber(redis.call('HGET', KEYS[1], 'analysisRequests') or '0')
            if analysis >= tonumber(ARGV[5]) or warmup + analysis >= tonumber(ARGV[4]) then return 0 end
            local success = tonumber(ARGV[8])
            if success > now - tonumber(ARGV[6]) then return 0 end
            if redis.call('EXISTS', KEYS[3]) == 1 then return 0 end
            redis.call('HINCRBY', KEYS[1], 'analysisRequests', 1)
            redis.call('PEXPIREAT', KEYS[1], ARGV[9])
            redis.call('PSETEX', KEYS[3], ARGV[7], tostring(now))
            return 1
            """.trimIndent(), Long::class.javaObjectType
        )
    }
}
