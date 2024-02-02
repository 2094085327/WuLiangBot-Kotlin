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
        cn["app_version"] = "2.40.1"
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

//            println("map:$returnMap")
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
        val n = "jEpJb9rRARU2rXDA9qYbZ3selxkuct9a"
        val t = (System.currentTimeMillis() / 1000.0).roundToInt()
        val random = Random()
        val chars = ('a'..'z') + ('0'..'9') + ('A'..'Z')
        val r = (1..6)
            .map { chars[random.nextInt(chars.size)] }
            .joinToString("")
        val DS = "salt=${n}&t=${t}&r=${r}".toMD5()
        return "${t},${r},${DS}"
    }

//    fun getDsSign(): String {
//        val n = "jEpJb9rRARU2rXDA9qYbZ3selxkuct9a"
//        val i = System.currentTimeMillis().toString()
//        val random = Random()
//        val chars = ('a'..'z') + ('0'..'9')+('A'..'Z')
//        val r = (1..6)
//            .map { chars[random.nextInt(chars.size)] }
//            .joinToString("")
//        val c = ("salt=$n&t=$i&r=$r").toMD5()
//        return "$r,$i,$c"
//    }

    private fun getDs(q: String = "", b: String = ""): String {
        var n = ""
        if (listOf("cn_gf01", "cn_qd01", "prod_gf_cn", "prod_qd_cn").contains(this.server)) {
            n = "xV8v4Qu54lUKrEYFZkJhB8cuOh9Asafs"
        } else if (this.server.startsWith("os_") || this.server.startsWith("official")) {
            n = "okr4obncj8bw5a65hbnn5oo6ixjc3l9w"
        }
        val t = System.currentTimeMillis() / 1000
        val r = (Math.random() * 900000 + 100000).toInt()
//        val DS = MessageDigest.getInstance("MD5").digest(("salt=$n&t=$t&r=$r&b=$b&q=$q").toByteArray())
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


//    private fun getServer(): String {
//        println("this.isSr:${this.isSr}")
//        val uid = this.uid
//        when (uid[0]) {
//            '1', '2' -> return if (this.isSr) "prod_gf_cn" else "cn_gf01" // 官服
////            '1', '2' -> return "cn_gf01" // 官服
//            '5' -> return if (this.isSr) "prod_qd_cn" else "cn_qd01" // B服
//            '6' -> return if (this.isSr) "prod_official_usa" else "os_usa" // 美服
//            '7' -> return if (this.isSr) "prod_official_euro" else "os_euro" // 欧服
//            '8' -> return if (this.isSr) "prod_official_asia" else "os_asia" // 亚服
//            '9' -> return if (this.isSr) "prod_official_cht" else "os_cht" // 港澳台服
//        }
//        return "cn_gf01"
//    }


}


//fun main() {
//    val mysApi = MysApi("144853327", "")
//    println(
//        MyApis(
//            "144853327",
//            "_MHYUUID=9d3a0f7f-ccca-432c-8a90-5402ee35aefc; DEVICEFP_SEED_ID=c9f4de775b4ed848; DEVICEFP_SEED_TIME=1685944486206; DEVICEFP=38d7eebae2cba; _MHYUUID=9d3a0f7f-ccca-432c-8a90-5402ee35aefc; LOGIN_PLATFORM_SWITCH_STATUS={%22bll8iq97cem8%22:{%22sms_login_tab%22:true%2C%22pwd_login_tab%22:true%2C%22password_reset_entry%22:true}}; cookie_token_v2=v2_ThThPNBBj8EEil2KU00yOR-ZELQB6tJ7XJd2sSizVK8jTnrPsyr6RqulbGHQIpbs2bkCz6QzImdq2n1zjQjh2Kqm5LLtagVCjpBGZmmencqEXe6Aq5TU; account_mid_v2=0wc65az3h1_mhy; account_id_v2=236687945; ltmid_v2=0wc65az3h1_mhy; ltuid_v2=236687945; ltoken_v2=v2_6tWqdwkIrkkoUVG9HgTtbOnGUP06pOzaIUXFfXoKQVRJkVD21nKE4QUllyLuwA63mPsOi6_ppjO_xzKrb0Tn7IUds1rMIwjg",
//            false
//        ).getGuid()
//    )

//    MyApis(
//        "144853327",
//        "_MHYUUID=9d3a0f7f-ccca-432c-8a90-5402ee35aefc; DEVICEFP_SEED_ID=c9f4de775b4ed848; DEVICEFP_SEED_TIME=1685944486206; DEVICEFP=38d7eebae2cba; _MHYUUID=9d3a0f7f-ccca-432c-8a90-5402ee35aefc; LOGIN_PLATFORM_SWITCH_STATUS={%22bll8iq97cem8%22:{%22sms_login_tab%22:true%2C%22pwd_login_tab%22:true%2C%22password_reset_entry%22:true}}; cookie_token_v2=v2_ThThPNBBj8EEil2KU00yOR-ZELQB6tJ7XJd2sSizVK8jTnrPsyr6RqulbGHQIpbs2bkCz6QzImdq2n1zjQjh2Kqm5LLtagVCjpBGZmmencqEXe6Aq5TU; account_mid_v2=0wc65az3h1_mhy; account_id_v2=236687945; ltmid_v2=0wc65az3h1_mhy; ltuid_v2=236687945; ltoken_v2=v2_6tWqdwkIrkkoUVG9HgTtbOnGUP06pOzaIUXFfXoKQVRJkVD21nKE4QUllyLuwA63mPsOi6_ppjO_xzKrb0Tn7IUds1rMIwjg",
//        false
//    ).getData("index")
//    MyApis(
//        "144853327",
//        "stuid=236687945;stoken=OCF7ZIMzHw8UEEzfucqhjDHjD2QBdgwNF9cEWwHV",
//        false
//    ).getData("GET_STOKEN_URL", mutableMapOf("login_ticket" to "ufnDUjU5EJ9LBunAXkqMKuSqmU1SRKWte8D4nT60"))
//    println(
//        MysApi(
//            "236687945",
//            "_MHYUUID=e83676f6-2d47-4fd3-bdf5-def0984cc272; DEVICEFP_SEED_ID=186581474beda0d1; DEVICEFP_SEED_TIME=1693822362398; DEVICEFP=38d7f043fc3d3; login_uid=236687945; login_ticket=40nhdFRP6hvH6cAQnbwok3EjSdofI5NJk9EkbhPg",
//            false
//        ).getData("GET_STOKEN_URL", mutableMapOf("login_ticket" to "40nhdFRP6hvH6cAQnbwok3EjSdofI5NJk9EkbhPg"))
//    )

//    println(
//        MysApi(
//            "144853327",
//            "stuid=236687945;stoken=puklfjyolioVRy0dr86h18Hf3Zk0oaTeMWep65FG",
//            false
//        ).getData("GET_COOKIE_TOKEN_URL", mutableMapOf("stoken" to "puklfjyolioVRy0dr86h18Hf3Zk0oaTeMWep65FG"))
//    )

//    val authKeyB = MysApi(
//        "144853327",
//        "stuid=236687945;stoken=puklfjyolioVRy0dr86h18Hf3Zk0oaTeMWep65FG;_MHYUUID=d5206ac6-9925-46d0-b458-4aa034f31022; DEVICEFP_SEED_ID=72ca146ec98f029b; DEVICEFP_SEED_TIME=1683446485601; mi18nLang=zh-cn; _ga_9TTX3TE5YL=GS1.1.1685954897.1.0.1685955609.0.0.0; _ga_71SQEB6JFR=GS1.1.1692430997.4.0.1692431389.0.0.0; DEVICEFP=38d7f0423f30b; LOGIN_PLATFORM_SWITCH_STATUS={%22cie2gjc0sg00%22:{%22pwd_login_tab%22:true%2C%22password_reset_entry%22:true%2C%22qr_login%22:true%2C%22sms_login_tab%22:true}}; _ga=GA1.1.1924844289.1685954898; _ga_KS4J8TXSHQ=GS1.1.1693812119.1.0.1693812121.0.0.0; cookie_token_v2=v2__uEnmOclRCBR9YnYyda4gXJ_UR0mbcECSVQtg5ePvAYm-8xzCovkQQXDnvcMIGZG5nXfqJsOSS-fSN2wFSMvoxPGd_ZFYkRfyyBwtcpaBUwATPXU_G70; account_mid_v2=0wc65az3h1_mhy; account_id_v2=236687945; ltoken_v2=v2_9eGJEzVXyl9C2KLfAq8wkVIJH2bKg2Q9MNpHE9xDF1Ijx-oz9NXbo8DjLfymkrw2Tgf6QY3ZD9-64_oimG2MX4fxwsEQKvnKpetGyQfpLI2sONjwTQL6; ltmid_v2=0wc65az3h1_mhy; ltuid_v2=236687945; login_uid=236687945; login_ticket=uezRm3OoSK8G1QrETNPQGOSFqSbGQxzaKiCOb0Si",
//    ).getData("authKeyB")
//
//    println(
//        authKeyB
//    )
//    val authKeyB = MysApi(
//        "144853327",
//        "stuid=236687945;stoken=puklfjyolioVRy0dr86h18Hf3Zk0oaTeMWep65FG;_MHYUUID=e83676f6-2d47-4fd3-bdf5-def0984cc272; DEVICEFP_SEED_ID=186581474beda0d1; DEVICEFP_SEED_TIME=1693822362398; DEVICEFP=38d7f043fc3d3; login_uid=236687945; login_ticket=40nhdFRP6hvH6cAQnbwok3EjSdofI5NJk9EkbhPg",
//    ).getData("accountInfo")
////
//    println(
//        authKeyB
//    )
//
//    val authKeyA = MysApi(
//        "144853327",
//        "stuid=236687945;stoken=puklfjyolioVRy0dr86h18Hf3Zk0oaTeMWep65FG",
//        false
//    ).getData("authKeyA")
//
//    println(
//        authKeyB
//    )
//
//    println(authKeyA)

//    println(
//        MysApi(
//            "144853327",
//            "ltoken=KD7xnMCkf9yYhkrSL2EcUPyfFXInLdaqluBOOcyG",
//        ).getData(
//            "gachaLog",
//            mutableMapOf(
//                "authkey" to URLEncoder.encode(authKeyB["data"]["authkey"].textValue(), "UTF-8"),
//                "size" to 20,
//                "end_id" to 0,
//                "page" to 1,
//                "gacha_type" to 301
//            )
//        )
//    )


//    val ticketUrl = mysApi.getData("qrCode", mutableMapOf("device" to "CBEC8312-AA77-489E-AE8A-8D498DE24E90"))
//    println(ticketUrl)
//
//    val url = ticketUrl["data"]["url"].textValue()
//    val ticket = url.substringAfter("ticket=")
//    println("url:$url")
//    println("ticket:$ticket")
//
//    QrCodeUtil.generate(
//        url,
//        300,
//        300,
//        FileUtil.file("C:/Users/86188/Desktop/bots/MiariDemo/src/main/kotlin/pers/wuliang/robot/botApi/genShin/qrCode.jpg")
//    )
//

//    try {
//        val qrConfig: QrConfig = QrConfig(300, 300)
//        QrCodeUtil.generate(qrCodeUrl, qrConfig, "png", response.getOutputStream())
//        log.info("生成二维码成功!")
//    } catch (e: cn.hutool.extra.qrcode.QrCodeException) {
//        log.error("发生错误！ {}！", e.message)
//    } catch (e: IOException) {
//        log.error("发生错误！ {}！", e.message)
//    }

//    var qrCodeStatus: JsonNode? = null
//
//    while ((qrCodeStatus == null) || (qrCodeStatus["data"]["stat"].textValue() != "Confirmed")) {
//        Thread.sleep(3000)
//        qrCodeStatus = mysApi.getData(
//            "qrCodeStatus",
//            mutableMapOf("device" to "CBEC8312-AA77-489E-AE8A-8D498DE24E90", "ticket" to ticket)
//        )
//        println(qrCodeStatus)
//    }
//
//    val gameTokenRaw = qrCodeStatus["data"]["payload"]["raw"].textValue()
//    val mapper = ObjectMapper()
//    val tokenJson = mapper.readTree(gameTokenRaw)
//    val accountId = tokenJson["uid"].textValue().toInt()
//
//    println(tokenJson["uid"].textValue().toInt())
//    println(tokenJson["token"].textValue())
//
//
//    val stoken = mysApi.getData(
//        "getStokenByGameToken",
//        mutableMapOf("accountId" to accountId, "gameToken" to tokenJson["token"].textValue())
//    )
//    println("stoken:$stoken")

//    val cookie = mysApi.getData(
//        "getCookieByGameToken",
//        mutableMapOf("accountId" to accountId, "gameToken" to tokenJson["token"].textValue())
//    )

//    println("cookie:$cookie")

//    mysApi.cookie =
//        "mid=${stoken["data"]["user_info"]["mid"].textValue()};stoken=${stoken["data"]["token"]["token"].textValue()}"
//
//    val authKeyB = mysApi.getData("authKeyB")
//    println(authKeyB)
//
//
//    println(
//        mysApi.getData(
//            "gachaLog",
//            mutableMapOf(
//                "authkey" to URLEncoder.encode(authKeyB["data"]["authkey"].textValue(), "UTF-8"),
//                "size" to 20,
//                "end_id" to 0,
//                "page" to 1,
//                "gacha_type" to 301
//            )
//        )
//    )


//    val authKeyB = MysApi(
//        "144853327",
//        "stuid=236687945;stoken=puklfjyolioVRy0dr86h18Hf3Zk0oaTeMWep65FG;_MHYUUID=d5206ac6-9925-46d0-b458-4aa034f31022; DEVICEFP_SEED_ID=72ca146ec98f029b; DEVICEFP_SEED_TIME=1683446485601; mi18nLang=zh-cn; _ga_9TTX3TE5YL=GS1.1.1685954897.1.0.1685955609.0.0.0; _ga_71SQEB6JFR=GS1.1.1692430997.4.0.1692431389.0.0.0; DEVICEFP=38d7f0423f30b; LOGIN_PLATFORM_SWITCH_STATUS={%22cie2gjc0sg00%22:{%22pwd_login_tab%22:true%2C%22password_reset_entry%22:true%2C%22qr_login%22:true%2C%22sms_login_tab%22:true}}; _ga=GA1.1.1924844289.1685954898; _ga_KS4J8TXSHQ=GS1.1.1693812119.1.0.1693812121.0.0.0; cookie_token_v2=v2__uEnmOclRCBR9YnYyda4gXJ_UR0mbcECSVQtg5ePvAYm-8xzCovkQQXDnvcMIGZG5nXfqJsOSS-fSN2wFSMvoxPGd_ZFYkRfyyBwtcpaBUwATPXU_G70; account_mid_v2=0wc65az3h1_mhy; account_id_v2=236687945; ltoken_v2=v2_9eGJEzVXyl9C2KLfAq8wkVIJH2bKg2Q9MNpHE9xDF1Ijx-oz9NXbo8DjLfymkrw2Tgf6QY3ZD9-64_oimG2MX4fxwsEQKvnKpetGyQfpLI2sONjwTQL6; ltmid_v2=0wc65az3h1_mhy; ltuid_v2=236687945; login_uid=236687945; login_ticket=uezRm3OoSK8G1QrETNPQGOSFqSbGQxzaKiCOb0Si",
//    ).getData("authKeyB")

//    "wBKMo7HKdtLYV7nbbvSEv3LDpluqgtkA1k2xYnf675hVklWWzP3ddXpZgbh%2Bw1zPyN8c6zcMaY5l2CvHSbZH88pG9dzRYTClbiEw6KIwYFN5%2BEXvdQw6nPtp3F9BDWZfesX5ji1nNeJwqJV%2B3AwSPNxx7MjQ5hQ36KxsZDgwi1yOP4LmOcOK3k/dIA8njY1nHN7nNOjNH/d9FCWbWD6rXFnu9Kz%2Bq1goHwNFGzf8GTDVGAleKeVmTpIFVjZyzfyQ7/xJJPcK8vUuSoNwzfsnkcQ38zMnxCe2NKjLIAH43GVMpVJKWBk1SvjReL5VrJ1N2AJbcN0VSYL7XbRVdHoiwA%3D%3D"
//}

