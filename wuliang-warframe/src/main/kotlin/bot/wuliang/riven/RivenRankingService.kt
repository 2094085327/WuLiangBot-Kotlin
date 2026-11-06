package bot.wuliang.riven

import bot.wuliang.botLog.logUtil.LoggerUtils.logError
import bot.wuliang.config.WARFRAME_WEEKLY_RIVEN_PC
import bot.wuliang.config.WfMarketConfig.WF_RIVEN_REROLLED_KEY
import bot.wuliang.config.WfMarketConfig.WF_RIVEN_UN_REROLLED_KEY
import bot.wuliang.entity.WfRivenEntity
import bot.wuliang.jacksonUtil.JacksonUtil
import bot.wuliang.market.history.MarketCollectionLease
import bot.wuliang.moudles.Riven
import bot.wuliang.redis.RedisService
import bot.wuliang.translation.PublicExportService
import bot.wuliang.utils.TimeUtils.getNextMonday
import okhttp3.OkHttpClient
import okhttp3.Request
import org.springframework.data.redis.core.RedisOperations
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.core.SessionCallback
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.*
import java.util.concurrent.TimeUnit
import javax.annotation.PreDestroy

data class RivenRankingMetadata(val sourceUpdatedAt: Long = 0, val fetchedAt: Long = 0)

/** 同步官方紫卡周榜并维护已洗、未洗分组缓存，不参与实时世界状态解析。 */
@Service
class RivenRankingService(
    private val redisService: RedisService,
    private val translations: PublicExportService,
    private val redis: RedisTemplate<String, Any>,
    private val strings: StringRedisTemplate,
    private val leases: MarketCollectionLease,
) {
    private val client = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS).build()

    fun ranking(rerolled: Boolean, localize: Boolean = true): List<Riven> {
        refreshRanking()
        val rows = raw(rerolled)
        return if (!localize) rows else rows.map { row ->
            row.copy(compatibility = (row.compatibilityId ?: row.itemType)?.let(translations::name))
        }
    }

    fun warmupStartsAt(day: LocalDate): Long = day.atTime(0, 10).toInstant(ZoneOffset.UTC).toEpochMilli()

    /** 图片跟随已发布的榜单变化，避免旧榜图片遮住恢复后的新榜。 */
    fun cacheTag(): String {
        val metadata = redisService.getValueOrThrow(METADATA_KEY) as? RivenRankingMetadata
        return "${metadata?.sourceUpdatedAt ?: 0}-${metadata?.fetchedAt ?: 0}"
    }

    /** 每日 UTC 00:10 后，且本周官方文件已经发布，才允许预热。 */
    fun readyForWarmup(now: Long): Boolean =
        now >= warmupStartsAt(Instant.ofEpochMilli(now).atOffset(ZoneOffset.UTC).toLocalDate()) && refreshRanking()

    /** 同武器取已洗、未洗热度的较大值；用 DE 原标识关联 WM 目录。 */
    fun popularWeapons(weapons: List<WfRivenEntity>): List<String> {
        val rows = raw(false) + raw(true)
        fun normalize(value: String) = value.lowercase().replace(Regex("[^a-z0-9]"), "")
        val candidates = weapons.flatMap { weapon ->
            listOfNotNull(weapon.enName, weapon.urlName).map { normalize(it) to weapon.urlName }
        }.groupBy({ it.first }, { it.second }).mapValues { (_, slugs) -> slugs.filterNotNull().distinct() }
        return rows.mapNotNull { row ->
            val name = row.compatibilityId?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val slug = candidates[normalize(name)]?.singleOrNull() ?: return@mapNotNull null
            slug to (row.pop ?: 0.0)
        }.groupBy({ it.first }, { it.second }).mapValues { it.value.maxOrNull() ?: 0.0 }
            .entries.sortedWith(compareByDescending<Map.Entry<String, Double>> { it.value }.thenBy { it.key })
            .map { it.key }
    }


    private fun raw(rerolled: Boolean): List<Riven> = requireNotNull(
        redisService.getValueTyped<List<Riven>>(if (rerolled) WF_RIVEN_REROLLED_KEY else WF_RIVEN_UN_REROLLED_KEY)
    ) { "紫卡周榜缓存不可用" }

    /** 旧榜可以展示，但不能用于本周预热。上游未更新或失败时十分钟后再检查。 */
    fun refreshRanking(): Boolean {
        val now = Instant.now()
        val monday = now.atOffset(ZoneOffset.UTC).toLocalDate()
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val weekStart = monday.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val metadata = redisService.getValueOrThrow(METADATA_KEY) as? RivenRankingMetadata
        val hasRows = redisService.hasKey(WF_RIVEN_UN_REROLLED_KEY) && redisService.hasKey(WF_RIVEN_REROLLED_KEY)
        if (hasRows && metadata != null && metadata.sourceUpdatedAt in weekStart..now.toEpochMilli()) return true
        if (hasRows && now.toEpochMilli() < warmupStartsAt(monday)) return false
        if ((strings.opsForValue().get(RETRY_KEY)?.toLongOrNull() ?: 0L) > now.toEpochMilli()) return false
        val owner = UUID.randomUUID().toString()
        if (!leases.acquire(LOCK_KEY, owner)) return false
        try {
            if ((strings.opsForValue().get(RETRY_KEY)?.toLongOrNull() ?: 0L) > now.toEpochMilli()) return false
            strings.opsForValue().set(RETRY_KEY, now.plusSeconds(600).toEpochMilli().toString(), Duration.ofMinutes(10))
            val request = Request.Builder().url(WARFRAME_WEEKLY_RIVEN_PC).header("Cache-Control", "no-cache").build()
            val (data, modifiedAt) = client.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "官方紫卡周榜 HTTP ${response.code}" }
                val modified = response.header("Last-Modified")?.let {
                    runCatching {
                        ZonedDateTime.parse(it, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
                    }.getOrNull()
                } ?: 0L
                response.body.string() to modified
            }
            val tree = JacksonUtil.readTree(JacksonUtil.convertSingleJsObjectToStandardJson(data))
            check(tree.isArray && !tree.isEmpty && tree.size() <= 10000) { "官方紫卡周榜内容异常" }
            val rows = tree.map { row ->
                check(row["rerolled"]?.isBoolean == true && row["pop"]?.isNumber == true) { "官方紫卡周榜字段异常" }
                Riven(
                    itemType = row["itemType"]?.textValue(), compatibility = row["compatibility"]?.textValue(),
                    compatibilityId = row["compatibility"]?.textValue(), rerolled = row["rerolled"].booleanValue(),
                    avg = row["avg"]?.doubleValue(), stddev = row["stddev"]?.doubleValue(),
                    min = row["min"]?.intValue(), max = row["max"]?.intValue(),
                    pop = row["pop"].doubleValue(), median = row["median"]?.doubleValue()
                )
            }
            val unrolled = rows.filter { it.rerolled == false }
            val rerolled = rows.filter { it.rerolled == true }
            check(unrolled.isNotEmpty() && rerolled.isNotEmpty()) { "官方紫卡周榜缺少分组" }
            check(leases.isOwner(LOCK_KEY, owner)) { "官方紫卡周榜刷新锁已失效" }
            // 旧榜多保留一天以应对发布延迟；分组和源修改时间在同一事务内发布。
            val ttl = Duration.between(Instant.now(), getNextMonday().plus(Duration.ofDays(1)))
            val result = redis.execute(object : SessionCallback<List<Any>> {
                override fun <K, V> execute(operations: RedisOperations<K, V>): List<Any> {
                    @Suppress("UNCHECKED_CAST")
                    val typed = operations as RedisOperations<String, Any>
                    typed.multi()
                    typed.opsForValue().set(WF_RIVEN_UN_REROLLED_KEY, unrolled, ttl)
                    typed.opsForValue().set(WF_RIVEN_REROLLED_KEY, rerolled, ttl)
                    typed.opsForValue().set(METADATA_KEY, RivenRankingMetadata(modifiedAt, now.toEpochMilli()), ttl)
                    return typed.exec()
                }
            })
            check(result.size == 3 && result.none { it is Throwable }) { "官方紫卡周榜缓存发布失败" }
            return modifiedAt in weekStart..Instant.now().toEpochMilli()
        } catch (error: Exception) {
            if (error is InterruptedException) {
                Thread.currentThread().interrupt()
                throw error
            }
            if (!hasRows) throw error
            logError("官方紫卡周榜刷新失败，保留有效旧榜，十分钟后重试", error)
            return false
        } finally {
            leases.release(LOCK_KEY, owner)
        }
    }

    @PreDestroy
    fun close() {
        client.dispatcher.executorService.shutdownNow()
        client.connectionPool.evictAll()
    }

    companion object {
        private const val METADATA_KEY = "Wuliang:Warframe:riven:updated"
        private const val LOCK_KEY = "Wuliang:Warframe:riven:refresh"
        private const val RETRY_KEY = "Wuliang:Warframe:riven:retry"
    }
}