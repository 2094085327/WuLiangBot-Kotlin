package bot.wuliang.worldstate.source

import bot.wuliang.config.WarframeProperties
import bot.wuliang.worldstate.model.WorldSnapshot
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import okhttp3.OkHttpClient
import okhttp3.Request
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

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

    /** 请求官方世界状态并读取 JSON 树；HTTP 非成功状态或缺少响应体时终止刷新。 */
    private fun load(): JsonNode =
        client.newCall(Request.Builder().url(settings.url).build()).execute().use { response ->
            check(response.isSuccessful) { "World state HTTP ${response.code}" }
            mapper.readTree(requireNotNull(response.body).string())
        }

    /** 读取带获取时间和过期标记的世界状态快照，由共用缓存协调刷新与失败回退。 */
    fun snapshot(): WorldSnapshot = cache.snapshot()
    /**
     * 读取世界状态 JSON 树的隔离副本。
     *
     * @param maxAgeMillis 允许复用的缓存年龄，单位为毫秒；默认值仍受数据源缓存策略约束。
     * @return 不会污染共享缓存的 JSON 树。
     */
    fun get(maxAgeMillis: Long = Long.MAX_VALUE): JsonNode = cache.snapshot(maxAgeMillis).data
}