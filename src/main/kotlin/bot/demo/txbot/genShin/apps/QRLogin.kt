package bot.demo.txbot.genShin.apps

import bot.demo.txbot.common.utils.WebImgUtil
import bot.demo.txbot.genShin.util.MysApi
import cn.hutool.extra.qrcode.QrCodeUtil
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.mikuac.shiro.annotation.AnyMessageHandler
import com.mikuac.shiro.annotation.MessageHandlerFilter
import com.mikuac.shiro.annotation.common.Shiro
import com.mikuac.shiro.common.utils.MsgUtils
import com.mikuac.shiro.core.Bot
import com.mikuac.shiro.dto.event.message.AnyMessageEvent
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.io.ByteArrayOutputStream
import java.util.regex.Matcher

/**
 *@Description:
 *@Author zeng
 *@Date 2023/9/8 10:13
 *@User 86188
 */
@Shiro
@Component
class QRLogin {
    @Autowired
    val webImgUtil = WebImgUtil()

    private val mysApi = MysApi("144853327", "")

    private fun getQrCodeStatus(ticket: String): JsonNode {
        return mysApi.getData(
            "qrCodeStatus", mutableMapOf("device" to "CBEC8312-AA77-489E-AE8A-8D498DE24E90", "ticket" to ticket)
        )
    }

    fun makeQrCode(): Pair<ByteArray, String> {
        val ticketUrl = mysApi.getData("qrCode", mutableMapOf("device" to "CBEC8312-AA77-489E-AE8A-8D498DE24E90"))
        val url = ticketUrl["data"]["url"].textValue()
        val ticket = url.substringAfter("ticket=")
        val outputStream = ByteArrayOutputStream()
        QrCodeUtil.generate(
            url,
            300,
            300,
            "jpg",
            outputStream
        )

        return Pair(outputStream.toByteArray(), ticket)
    }

    fun checkQrCode(ticket: String): Pair<JsonNode, Boolean> {
        var qrCodeStatus: JsonNode? = null
        while (qrCodeStatus?.get("data")?.get("stat")?.textValue() != "Confirmed") {
            qrCodeStatus = getQrCodeStatus(ticket)
            if (qrCodeStatus.get("message")?.textValue() == "ExpiredCode") {
                return Pair(qrCodeStatus, false)
            }
            Thread.sleep(1000)
        }
        return Pair(qrCodeStatus, true)
    }

    fun getStoken(qrCodeStatus: JsonNode): JsonNode {
        val gameTokenRaw = qrCodeStatus["data"]["payload"]["raw"].textValue()
        val mapper = ObjectMapper()
        val tokenJson = mapper.readTree(gameTokenRaw)
        val accountId = tokenJson["uid"].textValue().toInt()


        return mysApi.getData(
            "getStokenByGameToken",
            mutableMapOf("accountId" to accountId, "gameToken" to tokenJson["token"].textValue())
        )
    }

    fun getAccountInfo(stoken: JsonNode): JsonNode {
        mysApi.cookie =
            "mid=${stoken["data"]["user_info"]["mid"].textValue()};stoken=${stoken["data"]["token"]["token"].textValue()}"

        return mysApi.getData("getAccountInfo")
    }

    @Suppress("unused")
    fun getCookieStoken(qrCodeStatus: JsonNode): JsonNode {
        val gameTokenRaw = qrCodeStatus["data"]["payload"]["raw"].textValue()
        val mapper = ObjectMapper()
        val tokenJson = mapper.readTree(gameTokenRaw)
        val accountId = tokenJson["uid"].textValue().toInt()
        return mysApi.getData(
            "getCookieByGameToken",
            mutableMapOf("accountId" to accountId, "gameToken" to tokenJson["token"].textValue())
        )
    }

    @Suppress("unused")
    fun getHk4eToken(cookieToken: JsonNode): ArrayList<JsonNode> {
        val servers = arrayOf("cn_gf01", "cn_qd01")
        val accountId = cookieToken["data"]["uid"].textValue()
        val hk4eArray = arrayListOf<JsonNode>()
        mysApi.cookie =
            "account_id=$accountId;cookie_token=${cookieToken["data"]["cookie_token"].textValue()}"
        for (server in servers) {
            val hk4eToken = mysApi.getData(
                "getHk4eByCookieToken",
                mutableMapOf("region" to server, "uid" to accountId)
            )

            if (hk4eToken["retcode"].textValue() != "-1002") {
                hk4eArray.add(hk4eToken)
            }
        }
        return hk4eArray
    }

    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "#扫码登录")
    suspend fun getQRLogin(bot: Bot, event: AnyMessageEvent?, matcher: Matcher?) {
        bot.sendMsg(
            event,
            "免责声明:您将通过扫码完成获取米游社sk以及ck。\n本Bot将不会保存您的登录状态。\n我方仅提供米游社查询及相关游戏内容服务,若您的账号封禁、被盗等处罚与我方无关。\n害怕风险请勿扫码~",
            false
        )

        val (outputStream, ticket) = makeQrCode()
        val sendMsg: String = MsgUtils.builder().img(webImgUtil.outputStreamToBase64(outputStream)).build()
        bot.sendMsg(event, sendMsg, false)

        val (qrCodeStatus, checkQrCode) = checkQrCode(ticket)
        if (!checkQrCode) {
            bot.sendMsg(
                event,
                "二维码过期，请重新获取",
                false
            )
            return
        }
    }
}