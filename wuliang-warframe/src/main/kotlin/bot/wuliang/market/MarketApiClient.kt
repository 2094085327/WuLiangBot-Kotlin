package bot.wuliang.market

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
    ): JsonNode {
        val target = url.toHttpUrl().newBuilder().apply {
            params?.forEach { (key, value) -> addQueryParameter(key, value.toString()) }
        }.build()
        val request = Request.Builder().url(target).apply {
            header("Accept", "application/json")
            headers?.forEach { (key, value) -> header(key, value.toString()) }
            header("User-Agent", properties.userAgent)
        }.build()

        for (attempt in 1..properties.maxAttempts) {
            limiter.acquire(priority)
            try {
                client.newCall(request).execute().use { response ->
                    val body = response.body.string()
                    if (response.isSuccessful) return JacksonUtil.readTree(body)
                    if (response.code in setOf(429, 509)) {
                        limiter.cooldown(retryDelay(response.header("Retry-After"), attempt))
                    }
                    if (attempt == properties.maxAttempts || response.code !in setOf(429, 509, 500, 502, 503, 504)) {
                        throw HttpUtil.HttpException(response.code, body)
                    }
                }
            } catch (error: IOException) {
                if (Thread.currentThread().isInterrupted || attempt == properties.maxAttempts) throw error
            }
            Thread.sleep(1_000L shl (attempt - 1))
        }
        error("Market 请求未返回结果")
    }

    private fun retryDelay(retryAfter: String?, attempt: Int): Long {
        retryAfter?.toLongOrNull()?.let { return it.coerceIn(1, 300) * 1_000 }
        if (retryAfter != null) {
            runCatching {
                ZonedDateTime.parse(retryAfter, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()
            }
                .getOrNull()?.let { return it - System.currentTimeMillis() }
        }
        return 5_000L shl (attempt - 1)
    }

    @PreDestroy
    fun close() {
        client.dispatcher.executorService.shutdownNow()
        client.connectionPool.evictAll()
    }
}