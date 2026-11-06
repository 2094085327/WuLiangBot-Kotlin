package bot.wuliang.aipOcr

import bot.wuliang.aipOcr.AipOcrConstants.BAIDU_BASIC_OCR_URL
import bot.wuliang.aipOcr.AipOcrConstants.BAIDU_OAUTH_URL
import bot.wuliang.aipOcr.AipOcrConstants.BAIDU_POSITION_OCR_URL
import bot.wuliang.aipOcr.AipOcrConstants.BAIDU_TOKEN_REDIS_KEY
import bot.wuliang.jacksonUtil.JacksonUtil
import bot.wuliang.redis.RedisService
import com.fasterxml.jackson.databind.JsonNode
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.*
import java.util.concurrent.TimeUnit
import javax.annotation.PreDestroy

data class OcrLine(
    val text: String,
    val left: Int = 0,
    val top: Int = 0,
    val width: Int = 0,
    val height: Int = 0,
    val confidence: Double? = null,
)

class OcrUnavailableException(message: String) : RuntimeException(message)

/** 百度 OCR 使用表单 POST。 */
@Component
class AipOcrClient(
    @Value("\${aip_ocr.client_id}") var clientId: String,
    @Value("\${aip_ocr.client_secret}") var clientSecret: String,
    private val redisService: RedisService
) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        .build()

    fun getOauthToken(): String {
        (redisService.getValue(BAIDU_TOKEN_REDIS_KEY) as? String)?.takeIf(String::isNotBlank)?.let { return it }
        if (clientId.isBlank() || clientSecret.isBlank()) throw OcrUnavailableException("OCR 尚未配置，请联系管理员。")
        val token = post(
            BAIDU_OAUTH_URL, mapOf(
                "grant_type" to "client_credentials", "client_id" to clientId, "client_secret" to clientSecret,
            )
        )
        val value = token["access_token"]?.asText()?.takeIf(String::isNotBlank)
            ?: throw OcrUnavailableException("OCR 鉴权失败，请联系管理员检查配置。")
        redisService.setValueWithExpiry(
            BAIDU_TOKEN_REDIS_KEY,
            value,
            (token.path("expires_in").asLong(3600) - 60).coerceAtLeast(1),
            TimeUnit.SECONDS,
        )
        return value
    }

    fun getBasicOcr(params: Map<String, String>): JsonNode = recognize(BAIDU_BASIC_OCR_URL, params)

    /** 返回含坐标的文字，支持平台直接提供的图片字节和图片 URL。 */
    fun getImageText(url: String, bytes: ByteArray): List<OcrLine> {
        val image = when {
            bytes.isNotEmpty() && bytes.size <= 4 * 1024 * 1024 -> mapOf(
                "image" to Base64.getEncoder().encodeToString(bytes)
            )

            url.startsWith("https://", true) || url.startsWith("http://", true) -> mapOf("url" to url)
            bytes.isNotEmpty() -> throw OcrUnavailableException("图片过大，请裁剪为单张紫卡后重试。")
            else -> throw OcrUnavailableException("未获取到有效图片，请重新发送图片。")
        }
        val result = recognize(
            BAIDU_POSITION_OCR_URL, image + mapOf(
                "language_type" to "CHN_ENG", "detect_direction" to "true", "probability" to "true",
            )
        )
        val words = result["words_result"]?.takeIf(JsonNode::isArray)
            ?: throw OcrUnavailableException("OCR 未返回可识别文字，请发送清晰的单张紫卡图片。")
        return words.map { line ->
            val location = line.path("location")
            OcrLine(
                line.path("words").asText(), location.path("left").asInt(), location.path("top").asInt(),
                location.path("width").asInt(), location.path("height").asInt(),
                line.path("probability").get("average")?.takeIf(JsonNode::isNumber)?.asDouble(),
            )
        }.filter { it.text.isNotBlank() }.also {
            if (it.isEmpty()) throw OcrUnavailableException("未识别到文字，请发送清晰的单张紫卡图片。")
        }
    }

    private fun recognize(endpoint: String, params: Map<String, String>): JsonNode {
        for (attempt in 0..1) {
            val result = post("$endpoint?access_token=${getOauthToken()}", params)
            val code = result["error_code"]?.asInt() ?: return result
            if (code in setOf(110, 111) && attempt == 0) {
                redisService.deleteKey(BAIDU_TOKEN_REDIS_KEY)
                continue
            }
            throw OcrUnavailableException(
                when (code) {
                    17, 18, 19 -> "OCR 请求额度或频率受限，请稍后重试。"
                    216200, 216201, 216202, 216203 -> "OCR 无法读取图片，请重新发送清晰的单张紫卡图片。"
                    else -> "OCR 服务暂不可用（错误码 $code），请稍后重试。"
                }
            )
        }
        throw OcrUnavailableException("OCR 鉴权已失效，请联系管理员。")
    }

    private fun post(url: String, params: Map<String, String>): JsonNode {
        val body = FormBody.Builder().apply { params.forEach { (key, value) -> add(key, value) } }.build()
        val request = Request.Builder().url(url).post(body).build()
        try {
            return client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw OcrUnavailableException("OCR 服务暂不可用，请稍后重试。")
                JacksonUtil.readTree(response.body.string())
            }
        } catch (error: OcrUnavailableException) {
            throw error
        } catch (error: Exception) {
            if (Thread.currentThread().isInterrupted) throw error
            throw OcrUnavailableException("OCR 请求失败，请稍后重试。")
        }
    }

    @PreDestroy
    fun close() {
        client.dispatcher.executorService.shutdownNow()
        client.connectionPool.evictAll()
    }
}