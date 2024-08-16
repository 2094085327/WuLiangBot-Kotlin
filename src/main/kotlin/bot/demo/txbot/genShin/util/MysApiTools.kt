package bot.demo.txbot.genShin.util

import bot.demo.txbot.common.utils.HttpUtil
import bot.demo.txbot.common.utils.JacksonUtil
import bot.demo.txbot.common.utils.OtherUtil.STConversion.toMd5
import bot.demo.txbot.genShin.util.MysApi.Companion.server
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.io.File
import java.util.*
import kotlin.math.roundToInt


/**
 * @description: 米游社API的工具类
 * @author Nature Zero
 * @date 2024/7/22 下午4:22
 */
@Component
class MysApiTools @Autowired constructor(
    private val mysApi: MysApi,
) {
    companion object {
        var deviceId: String? = null
        var uid: String = ""
        var cookie: String = ""
    }

    private var objectMapper = ObjectMapper()


    data class HeaderConfig @JsonCreator constructor(
        @JsonProperty("app_version") val appVersion: String,
        @JsonProperty("User-Agent") val userAgent: String,
        @JsonProperty("client_type") val clientType: String,
        @JsonProperty("Origin") val origin: String,
        @JsonProperty("X_Requested_With") val xRequestedWith: String,
        @JsonProperty("Referer") val referer: String
    )

    data class Config(
        @JsonProperty("cn") val cn: HeaderConfig,
        @JsonProperty("os") val os: HeaderConfig
    )

    private fun loadConfig(): Config {
        val configFile = File(HEADERS_JSON)
        if (configFile.exists()) {
            val config = objectMapper.readValue(configFile, Config::class.java)
            return config
        } else throw Exception("配置文件不存在")
    }

    /**
     * 获取Key对应的API相关数据
     *
     * @param type 接口名称
     * @param data 请求参数
     * @param headersType 请求头类型
     * @return 请求结果
     */
    fun getData(
        type: String,
        data: MutableMap<String, Any?>? = null,
        headersType: String? = null
    ): JsonNode {
        val (url, headers, body) = getUrl(type, data)
        if (url.isNullOrEmpty()) throw Exception("请求地址为空")
        if (cookie.isNotEmpty()) headers?.set("Cookie", cookie)

        headersType?.let { headers?.set("x-rpc-client_type", headersType) }

        val response: JsonNode = if (!body.isNullOrEmpty()) {
            HttpUtil.doPostJson(url = url, jsonBody = body, headers = headers)
        } else {
            HttpUtil.doGetJson(url = url, headers = headers)
        }

        return response
    }

    /**
     * 获取请求地址
     *
     * @param type 接口名称
     * @param data 请求参数
     * @return url-请求连接, headers-请求头, body-请求体
     */
    fun getUrl(
        type: String,
        data: MutableMap<String, Any?>? = null
    ): Triple<String?, MutableMap<String, Any>?, String?> {
        val apiEndpoint = mysApi.getUrlApiEndpoint(type, data)
        apiEndpoint?.let {
            var url = it.url
            val query = it.query
            val body = it.body
            val sign = it.sign

            if (!query.isNullOrEmpty()) url += "?$query"
            val bodyJson =
                if (!body.isNullOrEmpty()) objectMapper.writeValueAsString(JacksonUtil.readTree(body)) else null
            val headers = getHeaders(query, bodyJson, sign)
            return Triple(url, headers, bodyJson)

        }

        return Triple(null, null, null)
    }

    /**
     * 获取请求头
     *
     * @param query 链接参数
     * @param body 请求体
     * @param sign 是否需要登录验证
     * @return 请求头
     */
    fun getHeaders(
        query: String?,
        body: String?,
        sign: Boolean? = false,
    ): MutableMap<String, Any> {
        val config = loadConfig()
        val device = "WL-${deviceId.toString().substring(0, 5)}"
        val client = if (server.startsWith("os")) config.os else config.cn

        val headers = mutableMapOf<String, Any>(
            "x-rpc-app_version" to client.appVersion,
            "x-rpc-client_type" to client.clientType,
            "User-Agent" to client.userAgent.replace("{device}", device).replace("{app_version}", client.appVersion),
            "Referer" to client.referer,
            "x-rpc-app_id" to "bll8iq97cem8"
        )

        if (sign == true) {
            headers["x-rpc-channel"] = "miyousheluodi"
            headers["x-rpc-device_id"] = deviceId.toString()
            headers["X-Requested-With"] = client.xRequestedWith
            headers["x-rpc-platform"] = "android"
            headers["x-rpc-device_model"] = device
            headers["x-rpc-device_name"] = device
            headers["x-rpc-sys_version"] = "6.0.1"
            headers["DS"] = getDsSign()
        } else {
            headers["DS"] = getDs(query, body)
        }

        return headers
    }


    @Suppress("unused")
    private fun getGuid(): String {
        fun s4(): String {
            return (((1 + Math.random()) * 0x10000).toInt() or 0).toString(16).substring(1)
        }

        return "${s4()}${s4()}-${s4()}-${s4()}-${s4()}-${s4()}${s4()}${s4()}"
    }


    /**
     * 签到ds
     */
    private fun getDsSign(): String {
        val n = SLAT_LK2
        val t = (System.currentTimeMillis() / 1000.0).roundToInt()
        val random = Random()
        val chars = ('a'..'z') + ('0'..'9') + ('A'..'Z')
        val r = (1..6)
            .map { chars[random.nextInt(chars.size)] }
            .joinToString("")
        val ds = "salt=${n}&t=${t}&r=${ r}".toMd5()
        return "${t},${r},${ds}"
    }

    private fun getDs(q: String? = "", b: String? = ""): String {
        val n = if (mysApi.androidServers.contains(server)) SLAT_4X
        else if (server.startsWith("os_") || server.startsWith("official")) SLAT_OS
        else ""

        val t = System.currentTimeMillis() / 1000
        val r = (Math.random() * 900000 + 100000).toInt()
        val ds = "salt=$n&t=$t&r=$r&b=$b&q=$q".toMd5()
        return "$t,$r,$ds"
    }
}