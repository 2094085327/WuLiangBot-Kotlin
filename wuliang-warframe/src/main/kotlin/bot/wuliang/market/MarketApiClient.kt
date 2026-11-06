package bot.wuliang.market

import bot.wuliang.botLog.logUtil.LoggerUtils.logInfo
import bot.wuliang.httpUtil.HttpUtil
import bot.wuliang.jacksonUtil.JacksonUtil
import com.fasterxml.jackson.databind.JsonNode
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.springframework.stereotype.Component
import java.io.IOException
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit
import javax.annotation.PreDestroy

/** 所有 WM HTTP 请求的公共入口，用户查询和后台采集共用预算。 */
@Component
class MarketApiClient(
    private val limiter: MarketRequestLimiter,
    private val properties: MarketProperties,
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(properties.requestTimeoutSeconds, TimeUnit.SECONDS)
        .callTimeout(properties.requestTimeoutSeconds, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .followRedirects(false)
        .build()

    fun getJson(
        url: String,
        headers: Map<String, Any>? = null,
        params: Map<String, Any>? = null,
        priority: MarketRequestPriority = MarketRequestPriority.INTERACTIVE,
        attempts: Int = properties.maxAttempts,
        budgetWaitMillis: Long? = null,
    ): JsonNode {
        require(attempts in 1..properties.maxAttempts)
        val target = url.toHttpUrl().newBuilder().apply {
            params?.forEach { (key, value) -> addQueryParameter(key, value.toString()) }
        }.build()
        val request = Request.Builder().url(target).apply {
            header("Accept", "application/json")
            headers?.forEach { (key, value) -> header(key, value.toString()) }
            header("User-Agent", properties.userAgent)
        }.build()

        for (attempt in 1..attempts) {
            limiter.acquire(priority, target.encodedPath == "/v1/auctions/search", budgetWaitMillis)
            try {
                client.newCall(request).execute().use { response ->
                    val body = response.body.string()
                    if (response.isSuccessful) return JacksonUtil.readTree(body)
                    if (response.code in setOf(429, 509)) {
                        val delay = limiter.cooldown(retryDelay(response.header("Retry-After"), body))
                        logInfo("WM HTTP ${response.code}：path=${target.encodedPath}, priority=$priority, " +
                            "attempt=$attempt/$attempts，共享退避=${delay}ms")
                    }
                    if (attempt == attempts || response.code !in setOf(429, 509, 500, 502, 503, 504)) {
                        throw HttpUtil.HttpException(response.code, body)
                    }
                }
            } catch (error: IOException) {
                if (Thread.currentThread().isInterrupted || attempt == attempts) throw error
            }
            Thread.sleep(1_000L shl (attempt - 1))
        }
        error("Market 请求未返回结果")
    }

    private fun retryDelay(retryAfter: String?, body: String): Long {
        val headerDelay = retryAfter?.toLongOrNull()?.let(::secondsToMillis) ?: runCatching {
            if (retryAfter == null) null else {
                ZonedDateTime.parse(retryAfter, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
                    .minus(System.currentTimeMillis()).coerceAtLeast(0)
            }
        }.getOrNull() ?: 0L
        val bodyDelay = runCatching {
            JacksonUtil.readTree(body).path("retry_after").asText().toLongOrNull()?.let(::secondsToMillis)
        }.getOrNull() ?: 0L
        return maxOf(headerDelay, bodyDelay)
    }

    private fun secondsToMillis(seconds: Long): Long = seconds.coerceIn(0, 31_536_000) * 1_000

    @PreDestroy
    fun close() {
        client.dispatcher.executorService.shutdownNow()
        client.connectionPool.evictAll()
    }
}