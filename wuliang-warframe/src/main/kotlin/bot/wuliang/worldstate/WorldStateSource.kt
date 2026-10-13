package bot.wuliang.worldstate

import bot.wuliang.config.WarframeProperties
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import okhttp3.OkHttpClient
import okhttp3.Request
import org.springframework.stereotype.Service
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit

/** fetchedAt 是本地成功获取时间；stale 表示获取失败后返回了旧快照。 */
data class WorldSnapshot(val data: JsonNode, val fetchedAt: Long, val stale: Boolean = false)

/** 在同一进程内合并并发获取，供后台、机器人指令和定时任务共享完整世界状态。 */
class WorldStateCache(
    private val load: () -> JsonNode,
    private val ttlMillis: Long = TimeUnit.SECONDS.toMillis(WarframeProperties.DEFAULT_CACHE_SECONDS),
    private val clock: Clock = Clock.systemUTC(),
    private val retryMillis: Long = TimeUnit.SECONDS.toMillis(WarframeProperties.DEFAULT_RETRY_SECONDS),
    private val staleMillis: Long = TimeUnit.SECONDS.toMillis(WarframeProperties.DEFAULT_STALE_SECONDS),
) {
    private var cached: WorldSnapshot? = null
    private var refreshAt = 0L
    private var retryAt = 0L

    // 获取过程持有锁，等待中的请求重新检查缓存，避免重复请求上游。
    @Synchronized
    fun snapshot(maxAgeMillis: Long = ttlMillis): WorldSnapshot {
        val now = clock.millis()
        val old = cached
        if (old != null && now < refreshAt && now - old.fetchedAt < maxAgeMillis.coerceIn(
                1,
                ttlMillis
            )
        ) return copy(old)
        if (now < retryAt) return fallback(old, now)
        try {
            val data = load()
            val fetchedAt = clock.millis()
            validate(data, fetchedAt)
            val result = WorldSnapshot(data.deepCopy(), fetchedAt)
            val deadline = refreshDeadline(result)
            // 校验与边界计算全部完成后才发布新快照，异常时保留原缓存。
            cached = result
            refreshAt = deadline
            retryAt = 0
            return copy(result)
        } catch (error: Exception) {
            val failedAt = clock.millis()
            retryAt = failedAt + retryMillis
            if (old == null || failedAt - old.fetchedAt >= staleMillis) throw WorldStateUnavailableException(error)
            return copy(old.copy(stale = true))
        }
    }

    private fun validate(data: JsonNode, now: Long) {
        require(data.isObject && data.path("Time").asLong() > 0 && data.path("SyndicateMissions").isArray) {
            "官方世界状态格式不完整"
        }
        require(now - data.path("Time").asLong() * 1000 < staleMillis) { "官方世界状态已过期" }
    }

    /** 有效期不能跨过已知激活/结束时间或 UTC 日更；发布延迟时短暂复用再重试。 */
    private fun refreshDeadline(snapshot: WorldSnapshot): Long {
        val publishedAt = snapshot.data.path("Time").asLong() * 1000
        val nextDay = Instant.ofEpochMilli(publishedAt).atZone(ZoneOffset.UTC).toLocalDate()
            .plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        var deadline = minOf(snapshot.fetchedAt + ttlMillis, nextDay)
        fun collect(node: JsonNode) {
            if (node.isObject) node.fields().forEach { (field, value) ->
                if (field == "Activation" || field == "Expiry") {
                    WorldStateTime.millis(value)?.takeIf { it > publishedAt }?.let { deadline = minOf(deadline, it) }
                } else if (value.isContainerNode) collect(value)
            } else if (node.isArray) node.forEach(::collect)
        }
        collect(snapshot.data)
        if (inventoryPending(snapshot)) deadline = minOf(deadline, snapshot.fetchedAt + retryMillis)
        return if (deadline <= snapshot.fetchedAt) snapshot.fetchedAt + minOf(ttlMillis, retryMillis) else deadline
    }

    private fun inventoryPending(snapshot: WorldSnapshot): Boolean = snapshot.data.path("VoidTraders").any { trader ->
        val activation = WorldStateTime.millis(trader["Activation"])
        val expiry = WorldStateTime.millis(trader["Expiry"])
        activation != null && expiry != null && snapshot.fetchedAt in activation until expiry && trader.path("Manifest").isEmpty
    }

    private fun fallback(old: WorldSnapshot?, now: Long): WorldSnapshot {
        if (old == null || now - old.fetchedAt >= staleMillis) throw WorldStateUnavailableException()
        return copy(old.copy(stale = true))
    }

    // JSON 树可变，返回深拷贝，防止解析调用污染共享缓存。
    private fun copy(value: WorldSnapshot) = value.copy(data = value.data.deepCopy())
}

class WorldStateUnavailableException(cause: Throwable? = null) :
    IllegalStateException("暂时无法获取官方世界状态，请稍后重试", cause)

/** 统一获取官方世界状态，玩法各自的 Redis 缓存由解析逻辑维护。 */
@Service
class WorldStateSource(properties: WarframeProperties) {
    private val settings = properties.worldState
    private val client = OkHttpClient.Builder().callTimeout(settings.requestTimeoutSeconds, TimeUnit.SECONDS).build()
    private val mapper = jacksonObjectMapper()
    private val cache = WorldStateCache(
        load = ::load,
        ttlMillis = TimeUnit.SECONDS.toMillis(settings.cacheSeconds),
        retryMillis = TimeUnit.SECONDS.toMillis(settings.retrySeconds),
        staleMillis = TimeUnit.SECONDS.toMillis(settings.staleSeconds),
    )

    private fun load(): JsonNode =
        client.newCall(Request.Builder().url(settings.url).build()).execute().use { response ->
            check(response.isSuccessful) { "World state HTTP ${response.code}" }
            mapper.readTree(requireNotNull(response.body).string())
        }

    fun snapshot(): WorldSnapshot = cache.snapshot()
    fun get(maxAgeMillis: Long = Long.MAX_VALUE): JsonNode = cache.snapshot(maxAgeMillis).data
}