package bot.wuliang.riven.samples

import bot.wuliang.botLog.logUtil.LoggerUtils.logError
import bot.wuliang.config.WARFRAME_MARKET_RIVEN_AUCTIONS_BASE
import bot.wuliang.httpUtil.HttpUtil
import bot.wuliang.market.MarketApiClient
import bot.wuliang.market.MarketProperties
import bot.wuliang.market.MarketRequestPriority
import bot.wuliang.market.history.MarketCollectionLease
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.time.Duration
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.annotation.PreDestroy

data class RivenSearchResponse(
    val observedAt: Long,
    val source: String,
    val response: JsonNode,
    val fetched: Boolean = false,
)

/** 所有紫卡搜索共用缓存与互斥锁。一个缓存未命中最多发出一次 HTTP 请求。 */
@Service
class RivenSampleSource(
    private val api: MarketApiClient,
    private val redis: StringRedisTemplate,
    private val json: ObjectMapper,
    private val properties: RivenSampleProperties,
    private val market: MarketProperties,
    private val store: RivenSampleStore,
    private val leases: MarketCollectionLease,
    private val collection: RivenWarmupState,
) {
    private val heartbeat = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "riven-query-lease").apply { isDaemon = true }
    }
    private val prefix get() = "${market.redisKeyPrefix}:riven"
    private val pauseKey get() = "$prefix:paused"

    fun queryKey(weapon: String, query: RivenSampleQuery): String = MessageDigest.getInstance("SHA-256")
        .digest(json.writeValueAsBytes(query.normalized().params(weapon).toSortedMap()))
        .joinToString("") { "%02x".format(it.toInt() and 255) }

    fun paused(): Boolean = redis.hasKey(pauseKey)

    /** 分析按缺口补查；先复用缓存，锁内复查缺口后原子占用自动采集额度。 */
    fun searchForAnalysis(
        weapon: String, query: RivenSampleQuery,
        stillNeeded: () -> Boolean
    ): RivenSearchResponse? {
        val key = queryKey(weapon, query)
        var lastSuccessAt = 0L
        return search(weapon, query, "ANALYSIS", fetchAllowed = {
            val now = System.currentTimeMillis()
            val state = store.observation(key)
            lastSuccessAt = state?.lastSuccessAt ?: 0L
            (state?.retryAfter ?: 0L) <= now && collection.analysisReady(weapon, lastSuccessAt, now)
        }, beforeFetch = {
            stillNeeded() && collection.reserveAnalysis(weapon, lastSuccessAt)
        })
    }

    fun search(
        weapon: String, query: RivenSampleQuery, source: String,
        priority: MarketRequestPriority = MarketRequestPriority.INTERACTIVE,
        fetchAllowed: (() -> Boolean)? = null,
        beforeFetch: (() -> Boolean)? = null
    ): RivenSearchResponse? {
        val normalized = query.normalized()
        val key = queryKey(weapon, normalized)
        val cacheKey = "$prefix:cache:$key"
        val lockKey = "$prefix:lock:$key"
        val failureKey = "$prefix:failure:$key"
        val owner = UUID.randomUUID().toString()
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(25)
        var pollMillis = 150L
        while (true) {
            cached(cacheKey)?.let {
                record(weapon, key, normalized, it)
                return it
            }
            if (fetchAllowed?.invoke() == false) return null
            check(!paused()) { "WM 紫卡查询暂处于冷却期" }
            check(!redis.hasKey(failureKey)) { "本条件查询暂处于冷却期" }
            if (leases.acquire(lockKey, owner)) break
            if (priority == MarketRequestPriority.BACKGROUND) return null
            check(System.nanoTime() < deadline) { "相同紫卡查询正在处理，请稍后重试" }
            Thread.sleep(pollMillis)
            pollMillis = (pollMillis * 2).coerceAtMost(1_000L)
        }
        val owned = AtomicBoolean(true)
        val renewal = heartbeat.scheduleAtFixedRate({
            try {
                if (!leases.renew(lockKey, owner)) owned.set(false)
            } catch (error: Exception) {
                owned.set(false); logError("紫卡查询续租失败", error)
            }
        }, 20, 20, TimeUnit.SECONDS)
        try {
            cached(cacheKey)?.let { record(weapon, key, normalized, it); return it }
            if (fetchAllowed?.invoke() == false) return null
            check(!paused() && !redis.hasKey(failureKey)) { "WM 紫卡查询暂处于冷却期" }
            if (beforeFetch?.invoke() == false) return null
            val response = api.getJson(
                "$WARFRAME_MARKET_RIVEN_AUCTIONS_BASE/search",
                headers = mapOf("Crossplay" to "true"),
                params = normalized.params(weapon), priority = priority, attempts = 1, budgetWaitMillis = 20_000
            )
            check(response.path("payload").path("auctions").isArray) { "紫卡搜索响应缺少 auctions 数组" }
            check(owned.get() && leases.isOwner(lockKey, owner)) { "紫卡查询锁已失效" }
            val result = RivenSearchResponse(System.currentTimeMillis(), source, response)
            val empty = response.path("payload").path("auctions").isEmpty
            record(weapon, key, normalized, result)
            redis.opsForValue().set(
                cacheKey, json.writeValueAsString(result),
                Duration.ofSeconds(if (empty) properties.emptyCacheSeconds else properties.cacheSeconds)
            )
            return result.copy(fetched = true)
        } catch (error: Exception) {
            if (error is InterruptedException) {
                Thread.currentThread().interrupt(); throw error
            }
            val http = error as? HttpUtil.HttpException
            val message = http?.let { "WM HTTP ${it.statusCode}" } ?: "紫卡查询失败：${error.javaClass.simpleName}"
            redis.opsForValue().set(failureKey, message, Duration.ofMinutes(properties.failureCooldownMinutes))
            if (http?.statusCode in setOf(403, 429, 509) || http?.responseBody?.contains("deprecated", true) == true) {
                redis.opsForValue().set(pauseKey, "1", Duration.ofHours(1))
            }
            if (properties.enabled) try {
                store.failure(weapon, key, normalized, source, System.currentTimeMillis(), message)
            } catch (recordError: Exception) {
                logError("紫卡查询失败状态保存失败", recordError)
            }
            throw error
        } finally {
            renewal.cancel(false)
            try {
                leases.release(lockKey, owner)
            } catch (error: Exception) {
                logError("紫卡查询锁释放失败，等待租约到期", error)
            }
        }
    }

    private fun cached(key: String): RivenSearchResponse? = redis.opsForValue().get(key)?.let {
        json.readValue(it, RivenSearchResponse::class.java)
    }

    private fun record(weapon: String, key: String, query: RivenSampleQuery, result: RivenSearchResponse) {
        if (!properties.enabled) return
        try {
            store.record(weapon, key, query, result.source, result.response, result.observedAt)
        } catch (error: Exception) {
            // 样本库故障不影响已经取得的用户查询；缓存复用不改写原观察时间。
            logError("紫卡查询结果保存失败：$weapon", error)
        }
    }

    @PreDestroy
    fun stop() {
        heartbeat.shutdownNow()
    }
}