package bot.demo.txbot.genShin.util

import bot.demo.txbot.common.utils.HttpUtil
import bot.demo.txbot.common.utils.JacksonUtil
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import java.math.BigInteger
import java.security.MessageDigest
import java.util.*
import kotlin.math.roundToInt


/**
 *@Description:
 *@Author zeng
 *@Date 2023/9/3 20:44
 *@User 86188
 */
class MysApi(
    var uid: String,
    var cookie: String,
) {

    private var server: String
    private val genShinApi: ApiTools
    var urlMap: MutableMap<String, Map<String, Any>> = mutableMapOf()

    init {
        this.server = getServerMys()
        this.genShinApi = ApiTools(uid, server)
    }

    private var objectMapper = ObjectMapper()

    private fun String.toMD5(): String {
        val md = MessageDigest.getInstance("MD5")
        val messageDigest = md.digest(toByteArray())
        val no = BigInteger(1, messageDigest)
        var hashText = no.toString(16)
        while (hashText.length < 32) {
            hashText = "0$hashText"
        }
        return hashText
    }


    fun getData(type: String, data: MutableMap<String, Any> = mutableMapOf(), headersType: String? = null): JsonNode {
        val (url, headers, body) = getUrl(type, data)
        headers["Cookie"] = cookie

        if (headersType != null) {
            headers["x-rpc-client_type"] = headersType
        }

        val response: JsonNode = if (body != "") {
            HttpUtil.doPostJson(url = url, jsonBody = body, headers = headers)
        } else {
            HttpUtil.doGetJson(url = url, headers = headers)
        }

        return response
    }


    private fun getUrl(type: String, data: MutableMap<String, Any>): Triple<String, MutableMap<String, Any>, String> {
        urlMap = genShinApi.getUrlMap(data)
        val urlInfo = urlMap[type]!!
        var url = urlInfo["url"] as String
        val query = urlInfo["query"] as? String ?: ""
        val body = urlInfo["body"] as? MutableMap<*, *> ?: mutableMapOf<String, Any>()
        val sign = urlInfo["sign"] as? Boolean ?: false
        var bodyJson = ""

        if (query.isNotEmpty()) url += "?$query"
        if (body.isNotEmpty()) bodyJson = objectMapper.writeValueAsString(JacksonUtil.readTree(body))

        val headers = getHeaders(query, bodyJson, sign)

        return Triple(url, headers, bodyJson)
    }

    private fun getHeaders(
        query: String,
        body: String,
        sign: Boolean = false,
        qrState: Boolean = false
    ): MutableMap<String, Any> {
        val device = "WL-${uid.toMD5().substring(0, 5)}"
        val cn: MutableMap<String, Any> = mutableMapOf()
        cn["app_version"] = APP_VERSION
        cn["User-Agent"] =
            "Mozilla/5.0 (Linux; Android 13; $device Build/TKQ1.220829.002; wv) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/99.0.4844.73 Mobile Safari/537.36 miHoYoBBS/${cn["app_version"]}"
        cn["client_type"] = "5"
        cn["Origin"] = "https://webstatic.mihoyo.com"
        cn["X_Requested_With"] = "com.mihoyo.hyperion"
        cn["Referer"] = "https://webstatic.mihoyo.com"

        val os: MutableMap<String, Any> = mutableMapOf()
        os["app_version"] = "2.9.0"
        os["User-Agent"] =
            "Mozilla/5.0 (Linux; Android 13; $device Build/TKQ1.220829.002; wv) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/99.0.4844.73 Mobile Safari/537.36 miHoYoBBS/${cn["app_version"]}"
        os["client_type"] = "2"
        os["Origin"] = "https://webstatic-sea.hoyolab.com"
        os["X_Requested_With"] = "com.mihoyo.hoyolab"
        os["Referer"] = "https://webstatic-sea.hoyolab.com"

        val client = if (server.startsWith("os")) os else cn

        val returnMap: MutableMap<String, Any> = mutableMapOf()
        if (sign) {
            returnMap["x-rpc-app_version"] = client["app_version"] as Any
            returnMap["x-rpc-client_type"] = client["client_type"] as Any
            returnMap["Referer"] = client["Referer"] as Any
            returnMap["x-rpc-channel"] = "miyousheluodi"
//            if (body.contains("game_biz")){
//                returnMap["x-rpc-client_type"] = "2"
//                returnMap["Referer"] = "https://app.mihoyo.com"
//                returnMap["Host"] = "api-takumi.mihoyo.com"
//                returnMap["Origin"] = "api-takumi.miyoushe.com"
//                returnMap["x-rpc-channel"] = "mihoyo"
//                returnMap["x-rpc-sys_version"] = "12"
//            }
//            returnMap["x-rpc-device_id"] = getGuid()
            returnMap["x-rpc-device_id"] = "CBEC8312-AA77-489E-AE8A-8D498DE24E90"
            returnMap["User-Agent"] = client["User-Agent"] as Any
            returnMap["X-Requested-With"] = client["X_Requested_With"] as Any
            returnMap["x-rpc-platform"] = "android"
            returnMap["x-rpc-device_model"] = device
            returnMap["x-rpc-device_name"] = device
            returnMap["x-rpc-sys_version"] = "6.0.1"
            returnMap["x-rpc-app_id"] = "bll8iq97cem8"
            returnMap["DS"] = getDsSign()

            return returnMap
        } else {
            returnMap["x-rpc-app_version"] = client["app_version"] as Any
            returnMap["x-rpc-client_type"] = client["client_type"] as Any
            returnMap["User-Agent"] = client["User-Agent"] as Any
            returnMap["Referer"] = client["Referer"] as Any
            returnMap["x-rpc-app_id"] = "bll8iq97cem8"
            returnMap["DS"] = getDs(query, body)
            return returnMap
        }
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
        val ds = "salt=${n}&t=${t}&r=${r}".toMD5()
        return "${t},${r},${ds}"
    }

    private fun getDs(q: String = "", b: String = ""): String {
        var n = ""
        if (listOf("cn_gf01", "cn_qd01", "prod_gf_cn", "prod_qd_cn").contains(this.server)) {
            n = "xV8v4Qu54lUKrEYFZkJhB8cuOh9Asafs"
        } else if (this.server.startsWith("os_") || this.server.startsWith("official")) {
            n = "okr4obncj8bw5a65hbnn5oo6ixjc3l9w"
        }
        val t = System.currentTimeMillis() / 1000
        val r = (Math.random() * 900000 + 100000).toInt()
        val DS = "salt=$n&t=$t&r=$r&b=$b&q=$q".toMD5()
        return "$t,$r,$DS"
    }

    private fun getServerMys(): String {
        return when (uid[0]) {
            '1', '2', '3' -> "cn_gf01" // 官服
            '5' -> "cn_qd01" // B服
            '6' -> "os_usa" // 美服
            '7' -> "os_euro" // 欧服
            '8' -> "os_asia" // 亚服
            '9' -> "os_cht" // 港澳台服
            else -> "cn_gf01"
        }
    }
}