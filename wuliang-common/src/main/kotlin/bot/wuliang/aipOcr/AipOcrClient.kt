package bot.wuliang.aipOcr

import bot.wuliang.aipOcr.AipOcrConstants.BAIDU_BASIC_OCR_URL
import bot.wuliang.aipOcr.AipOcrConstants.BAIDU_OAUTH_URL
import bot.wuliang.aipOcr.AipOcrConstants.BAIDU_TOKEN_REDIS_KEY
import bot.wuliang.httpUtil.HttpUtil
import bot.wuliang.redis.RedisService
import com.fasterxml.jackson.databind.JsonNode
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

@Component
class AipOcrClient(
    @Value("\${aip_ocr.client_id}") var clientId: String,
    @Value("\${aip_ocr.client_secret}") var clientSecret: String,
    @Autowired private val redisService: RedisService
) {

    fun getOauthToken(): String {
        if (redisService.hasKey(BAIDU_TOKEN_REDIS_KEY)) {
            return redisService.getValue(BAIDU_TOKEN_REDIS_KEY).toString()
        }
        val params = mutableMapOf(
            "grant_type" to "client_credentials",
            "client_id" to clientId,
            "client_secret" to clientSecret
        )
        val tokenJson = HttpUtil.doGetJson(BAIDU_OAUTH_URL, params = params)
        redisService.setValueWithExpiry(
            BAIDU_TOKEN_REDIS_KEY, tokenJson["access_token"].textValue(), tokenJson["expires_in"].longValue(),
            TimeUnit.SECONDS
        )
        return tokenJson["access_token"].textValue()
    }

    fun getBasicOcr(params: Map<String, String>): JsonNode {
        return HttpUtil.doGetJson("$BAIDU_BASIC_OCR_URL?access_token=${getOauthToken()}", params = params)
    }
}