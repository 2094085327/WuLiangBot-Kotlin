package bot.wuliang.worldstate

import bot.wuliang.botLog.logUtil.LoggerUtils.logWarn
import bot.wuliang.config.WarframeProperties
import bot.wuliang.config.WfMarketConfig.WF_WIKI_WEAPON_ROTATION_KEY_PREFIX
import bot.wuliang.redis.RedisService
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import okhttp3.OkHttpClient
import okhttp3.Request
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.util.*
import java.util.concurrent.TimeUnit

data class WeaponRotationReport(
    val kind: String,
    val title: String,
    val activation: Long,
    val expiry: Long,
    val batch: String?,
    val sourceUrl: String,
    val wikiUpdatedAt: Long?,
    val fetchedAt: Long?,
    val stale: Boolean,
    val awaitingUpdate: Boolean,
    val weapons: List<WikiWeaponOffer>,
    val note: String,
)

/** Wiki 社区记录独立于官方世界状态；按需读取，换期时重新获取，失败时短暂复用并标记。 */
@Service
class WikiWeaponRotationSource(properties: WarframeProperties, private val redis: RedisService) {
    private val settings = properties.wikiRotation
    private val client = OkHttpClient.Builder().callTimeout(settings.requestTimeoutSeconds, TimeUnit.SECONDS).build()
    private val mapper = jacksonObjectMapper()

    private data class Cached(val page: WikiWeaponPage, val fetchedAt: Long, val periodStart: Long)

    private val sourceHash = MessageDigest.getInstance("SHA-256")
        .digest(settings.baseUrl.trimEnd('/').toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

    /** 按轮换类型顺序获取信条与终幕武器报告，复用各自的获取和缓存策略。 */
    fun reports(): List<WeaponRotationReport> = WeaponRotationKind.entries.map(::report)

    /** 获取指定武器类型的轮换报告；在同步锁内复用缓存，异常时返回带原因的不可用报告。 */
    @Synchronized
    fun report(kind: WeaponRotationKind): WeaponRotationReport {
        return try {
            cachedReport(kind)
        } catch (error: Exception) {
            // Redis 不可用时不绕过缓存直连 Wiki，避免每个请求都打到上游。
            logWarn("{} Wiki 轮换缓存不可用：{}", kind.title, error.message, error)
            unavailable(kind, System.currentTimeMillis(), "轮换缓存暂时不可用，请稍后重试")
        }
    }

    /** 校验内存及 Redis 缓存所属轮次，按刷新间隔获取新报告；失败时仅在允许时限内回退。 */
    private fun cachedReport(kind: WeaponRotationKind): WeaponRotationReport {
        val now = System.currentTimeMillis()
        val period = kind.period(now)
        val key = cacheKey(kind, period.activation)
        val old = readCache(key, kind, now)
        if (old != null && isFresh(old, now)) return render(kind, old, now, false)
        if (redis.getValueOrThrow("$key:retry") != null) return fallback(kind, old, now)

        val lockKey = "$key:refresh"
        val token = UUID.randomUUID().toString()
        val lockMillis = minOf(TimeUnit.SECONDS.toMillis(settings.requestTimeoutSeconds) + 5_000, period.expiry - now)
        if (!redis.setValueIfAbsent(lockKey, token, lockMillis, TimeUnit.MILLISECONDS)) {
            val latestNow = System.currentTimeMillis()
            val latest = readCache(cacheKey(kind, kind.period(latestNow).activation), kind, latestNow)
            return if (latest != null && isFresh(latest, latestNow)) render(kind, latest, latestNow, false)
            else fallback(kind, latest, latestNow, "Wiki 轮换数据正在刷新，请稍后重试", "正在刷新，暂时使用本期缓存")
        }
        try {
            // 等待锁的过程中其他实例可能已经完成刷新或发布了重试退避状态。
            val checkedAt = System.currentTimeMillis()
            val latest = readCache(key, kind, checkedAt)
            if (latest != null && isFresh(latest, checkedAt)) return render(kind, latest, checkedAt, false)
            if (redis.getValueOrThrow("$key:retry") != null) return fallback(kind, latest, checkedAt)
            val fresh = try {
                fetch(kind)
            } catch (error: Exception) {
                val failedAt = System.currentTimeMillis()
                val failedPeriod = kind.period(failedAt)
                // 旧轮换的请求失败不影响新轮换的获取。
                if (failedPeriod.activation == period.activation) {
                    redis.setValueWithExpiry(
                        "$key:retry",
                        true,
                        minOf(TimeUnit.SECONDS.toMillis(settings.retrySeconds), failedPeriod.expiry - failedAt),
                        TimeUnit.MILLISECONDS
                    )
                }
                logWarn("{} Wiki 轮换数据获取失败：{}", kind.title, error.message, error)
                return fallback(kind, latest, failedAt)
            }
            val freshPeriod = kind.period(fresh.fetchedAt)
            val ttlMillis =
                minOf(TimeUnit.SECONDS.toMillis(settings.staleSeconds), freshPeriod.expiry - fresh.fetchedAt)
            // JSON 字符串避免 Redis 的多态序列化绑定私有缓存类及集合的 JVM 类型。
            redis.setValueWithExpiry(
                cacheKey(kind, fresh.periodStart),
                mapper.writeValueAsString(fresh),
                ttlMillis,
                TimeUnit.MILLISECONDS
            )
            return render(kind, fresh, fresh.fetchedAt, false)
        } finally {
            try {
                redis.deleteValueIfMatches(lockKey, token)
            } catch (error: Exception) {
                logWarn("{} Wiki 轮换刷新锁释放失败，将按有效期自动释放：{}", kind.title, error.message, error)
            }
        }
    }

    /** 下载指定武器类型的 Wiki 编辑页并解析，记录获取时刻与所属轮换区间。 */
    private fun fetch(kind: WeaponRotationKind): Cached {
        // 沿用“本地翻译暂存”中已验证的公开编辑页请求方式。
        val request = Request.Builder().url(pageUrl(kind) + "?action=edit")
            .header("User-Agent", "okhttp/4.12.0")
            .header("Accept", "text/html,application/xhtml+xml").build()
        val html = client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { "Wiki HTTP ${response.code}" }
            requireNotNull(response.body).string()
        }
        val page = WikiWeaponRotationParser.parse(html, kind)
        val fetchedAt = System.currentTimeMillis()
        check(page.updatedAt <= fetchedAt) { "Wiki 修订时间晚于当前时间" }
        return Cached(page, fetchedAt, kind.period(fetchedAt).activation)
    }

    /** 按来源指纹、武器类型及轮换开始时间生成独立的 Redis 缓存键。 */
    private fun cacheKey(kind: WeaponRotationKind, periodStart: Long) =
        "$WF_WIKI_WEAPON_ROTATION_KEY_PREFIX$sourceHash:${kind.name.lowercase()}:$periodStart"

    /** 读取并校验 Redis 中的 Wiki 记录；损坏、轮次不符或过旧的缓存返回 null。 */
    private fun readCache(key: String, kind: WeaponRotationKind, now: Long): Cached? {
        val raw = redis.getValueOrThrow(key) ?: return null
        return try {
            val value = mapper.readValue(raw as String, Cached::class.java)
            require(
                value.periodStart == kind.period(now).activation && value.fetchedAt in value.periodStart..now &&
                        now - value.fetchedAt < TimeUnit.SECONDS.toMillis(settings.staleSeconds)
            ) { "缓存已超出本期有效范围" }
            require(
                value.page.batches.keys == if (kind == WeaponRotationKind.CODA) setOf(
                    "A",
                    "B"
                ) else setOf("TENET")
            ) {
                "缓存缺少武器分组"
            }
            value
        } catch (error: Exception) {
            logWarn("{} Wiki 轮换缓存无效，重新获取：{}", kind.title, error.message, error)
            redis.deleteKey(key)
            null
        }
    }

    /** 判断缓存记录距 [now] 是否仍在配置的正常刷新间隔内。 */
    private fun isFresh(value: Cached, now: Long) =
        now - value.fetchedAt < TimeUnit.SECONDS.toMillis(settings.cacheSeconds)

    /** 在允许的旧数据有效期内生成带过期说明的报告，不能回退时生成不可用报告。 */
    private fun fallback(
        kind: WeaponRotationKind, value: Cached?, now: Long,
        message: String = "暂时无法获取 Wiki 社区轮换记录，请稍后重试",
        cacheNote: String = "获取失败，暂时使用本期缓存"
    ): WeaponRotationReport {
        if (value != null && value.periodStart == kind.period(now).activation &&
            now - value.fetchedAt < TimeUnit.SECONDS.toMillis(settings.staleSeconds)
        ) return render(kind, value, now, true, cacheNote)
        return unavailable(kind, now, message)
    }

    /** 生成无可用武器列表的轮换报告，保留当前轮次、来源地址和错误说明。 */
    private fun unavailable(kind: WeaponRotationKind, now: Long, message: String): WeaponRotationReport {
        val current = kind.period(now)
        return WeaponRotationReport(
            kind.name.lowercase(), kind.title, current.activation, current.expiry,
            current.batch, pageUrl(kind), null, null, false, false, emptyList(), message
        )
    }

    /** 将配置的 Wiki 基础地址与武器类型的页面名称拼接为来源 URL。 */
    private fun pageUrl(kind: WeaponRotationKind) = settings.baseUrl.trimEnd('/') + "/" + kind.page

    /** 根据当前轮次选择武器批次，并附上获取时间、修订时间及过期或等待更新标记。 */
    private fun render(
        kind: WeaponRotationKind, value: Cached, now: Long, stale: Boolean,
        cacheNote: String = "获取失败，暂时使用本期缓存"
    ): WeaponRotationReport {
        val period = kind.period(now)
        val awaitingUpdate = value.page.updatedAt < period.activation
        val note = listOfNotNull(
            "数据来自 Warframe Wiki 玩家报告",
            if (awaitingUpdate) "页面最近修订早于本期开始，以下为已有记录，本期加成待更新" else null,
            if (stale) cacheNote else null,
        ).joinToString("；")
        return WeaponRotationReport(
            kind.name.lowercase(), kind.title, period.activation, period.expiry,
            period.batch, pageUrl(kind), value.page.updatedAt, value.fetchedAt, stale, awaitingUpdate,
            value.page.batches.getValue(period.batch ?: "TENET"), note
        )
    }
}