package bot.wuliang.worldstate.source

import bot.wuliang.config.WarframeProperties
import bot.wuliang.worldstate.model.WorldSnapshot
import bot.wuliang.worldstate.parser.WorldStateTime
import com.fasterxml.jackson.databind.JsonNode
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.concurrent.TimeUnit

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

    /**
     * 返回允许年龄范围内的快照，必要时合并并发刷新请求；失败时按旧快照有效期决定回退或报错。
     *
     * @param maxAgeMillis 调用方允许的缓存年龄，单位为毫秒，受全局缓存时长约束。
     * @return 隔离 JSON 树的快照副本，回退旧数据时 stale 为 true。
     * @throws WorldStateUnavailableException 刷新失败且没有可用旧快照时抛出。
     */
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

    /** 校验官方数据的必要字段及发布时间，拒绝格式不完整或超过旧数据有效期的快照。 */
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
        /** 递归收集未来的激活和结束时间，收紧当前快照的刷新截止时间。 */
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

    /** 判断快照中是否存在已经到访但官方尚未发布库存的商人。 */
    private fun inventoryPending(snapshot: WorldSnapshot): Boolean = snapshot.data.path("VoidTraders").any { trader ->
        val activation = WorldStateTime.millis(trader["Activation"])
        val expiry = WorldStateTime.millis(trader["Expiry"])
        activation != null && expiry != null && snapshot.fetchedAt in activation until expiry && trader.path("Manifest").isEmpty
    }

    /** 在允许的旧数据年龄内返回带过期标记的副本；没有可用旧快照时抛出数据不可用异常。 */
    private fun fallback(old: WorldSnapshot?, now: Long): WorldSnapshot {
        if (old == null || now - old.fetchedAt >= staleMillis) throw WorldStateUnavailableException()
        return copy(old.copy(stale = true))
    }

    // JSON 树可变，返回深拷贝，防止解析调用污染共享缓存。
    /** 深拷贝快照中的可变 JSON 树，避免调用方修改共享缓存数据。 */
    private fun copy(value: WorldSnapshot) = value.copy(data = value.data.deepCopy())
}