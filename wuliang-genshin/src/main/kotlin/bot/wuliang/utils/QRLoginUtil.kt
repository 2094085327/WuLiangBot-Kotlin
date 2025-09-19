package bot.wuliang.utils

import bot.wuliang.botLog.logUtil.LoggerUtils.logError
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.io.ByteArrayOutputStream

/**
 *@Description:
 *@Author zeng
 *@Date 2023/9/8 10:13
 *@User 86188
 */
@Component
class QRLoginUtil(
    @Autowired val mysApiTools: MysApiTools
) {


    /**
     * 获取二维码状态
     *
     * @param ticket 创建二维码时的ticket
     * @return 获取二维码状态
     */
    private fun getQrCodeStatus(ticket: String): JsonNode {
        return mysApiTools.getData("qrCodeStatus", mutableMapOf("device" to MysApiTools.deviceId, "ticket" to ticket))
    }

    /**
     * 创建二维码
     *
     * @return 二维码输出流及二维码创建时返回的ticket
     */
    fun makeQrCode(): Pair<ByteArray, String> {
        val ticketUrl = mysApiTools.getData("qrCode", mutableMapOf("device" to MysApiTools.deviceId))
        val url = ticketUrl["data"]["url"].textValue()
        val ticket = url.substringAfter("ticket=")
        val outputStream = ByteArrayOutputStream()
        QrCodeUtils.encode(url, outputStream)

        return Pair(outputStream.toByteArray(), ticket)
    }

    /**
     * 检查二维码状态
     *
     * @param ticket 二维码创建时返回的ticket
     * @return 二维码状态
     */
    fun checkQrCode(ticket: String): Pair<JsonNode, Boolean> {
        val errorCodes = setOf(-3501, -3503, -105, -106)
        var qrCodeStatus: JsonNode? = null
        val maxRetries = 60 * 5  // 设置最大轮询次数，避免出现未知错误导致的死循环

        repeat(maxRetries) {
            qrCodeStatus = getQrCodeStatus(ticket)

            qrCodeStatus?.let {
                val retcode = it["retcode"].intValue()
                val message = it["message"]?.textValue()

                if (it.get("data")?.get("stat")?.textValue() == "Confirmed") {
                    return Pair(it, true)
                }

                if (message == "ExpiredCode" || retcode in errorCodes) {
                    return Pair(it, false)
                }
            }

            Thread.sleep(1000)
        }

        // 如果超过最大轮询次数未确认，则返回 false，并创建一个包含错误信息的 JsonNode
        val errorNode: ObjectNode = JsonNodeFactory.instance.objectNode()
        errorNode.put("message", "未知错误，超过二维码轮询次数")
        logError("获取二维码状态失败：${qrCodeStatus ?: errorNode}")
        return Pair(qrCodeStatus ?: errorNode, false)
    }

    /**
     * 获取stoken
     *
     * @param qrCodeStatus 成功的二维码状态信息
     * @return stoken
     */
    fun getStoken(qrCodeStatus: JsonNode): JsonNode {
        val gameTokenRaw = qrCodeStatus["data"]["payload"]["raw"].textValue()
        val mapper = ObjectMapper()
        // 米哈游这个数据结构比较抽象，token是嵌套在上一个Json中的，全部经过了一遍转义的一个字符串，所以需要再重新转为Json
        val tokenJson = mapper.readTree(gameTokenRaw)
        val accountId = tokenJson["uid"].textValue().toInt()
        return mysApiTools.getData(
            "getStokenByGameToken",
            mutableMapOf("accountId" to accountId, "gameToken" to tokenJson["token"].textValue())
        )
    }

    fun getAccountInfo(stoken: JsonNode): JsonNode {
        MysApiTools.cookie =
            "mid=${stoken["data"]["user_info"]["mid"].textValue()};stoken=${stoken["data"]["token"]["token"].textValue()}"

        return mysApiTools.getData("getAccountInfo")
    }

    @Suppress("unused")
    fun getCookieStoken(qrCodeStatus: JsonNode): JsonNode {
        val gameTokenRaw = qrCodeStatus["data"]["payload"]["raw"].textValue()
        val mapper = ObjectMapper()
        val tokenJson = mapper.readTree(gameTokenRaw)
        val accountId = tokenJson["uid"].textValue().toInt()
        return mysApiTools.getData(
            "getCookieByGameToken",
            mutableMapOf("accountId" to accountId, "gameToken" to tokenJson["token"].textValue())
        )
    }

    @Suppress("unused")
    fun getHk4eToken(cookieToken: JsonNode): ArrayList<JsonNode> {
        val servers = arrayOf("cn_gf01", "cn_qd01")
        val accountId = cookieToken["data"]["uid"].textValue()
        val hk4eArray = arrayListOf<JsonNode>()
        MysApiTools.cookie =
            "account_id=$accountId;cookie_token=${cookieToken["data"]["cookie_token"].textValue()}"
        for (server in servers) {
            val hk4eToken = mysApiTools.getData(
                "getHk4eByCookieToken",
                mutableMapOf("region" to server, "uid" to accountId)
            )

            if (hk4eToken["retcode"].textValue() != "-1002") {
                hk4eArray.add(hk4eToken)
            }
        }
        return hk4eArray
    }
}