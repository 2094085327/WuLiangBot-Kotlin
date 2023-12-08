package bot.demo.txbot.genShin.apps

import bot.demo.txbot.common.utils.WebImgUtil
import bot.demo.txbot.genShin.database.gacha.GaChaService
import bot.demo.txbot.genShin.database.genshin.GenShinService
import bot.demo.txbot.genShin.util.MysApi
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.mikuac.shiro.annotation.AnyMessageHandler
import com.mikuac.shiro.annotation.MessageHandlerFilter
import com.mikuac.shiro.annotation.common.Shiro
import com.mikuac.shiro.common.utils.MsgUtils
import com.mikuac.shiro.core.Bot
import com.mikuac.shiro.dto.event.message.AnyMessageEvent
import kotlinx.coroutines.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.io.File
import java.io.FileOutputStream
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Matcher


/**
 *@Description:
 *@Author zeng
 *@Date 2023/9/8 17:01
 *@User 86188
 */
@Shiro
@Component
class GachaLog {

    @Autowired
    lateinit var gaChaService: GaChaService

    @Autowired
    lateinit var genShinService: GenShinService

    private val qrLogin = QRLogin()
    private var mysApi = MysApi("0", "")
    private val objectMapper = ObjectMapper()

    fun test2(gachaId: String, type: String): MutableList<MutableMap<String, Any>> {
        val typeString = if (type == "up") {
            "r5_up_items"
        } else {
            "r5_prob_list"
        }
        val jsonData = mysApi.getData("gacha_Info", mutableMapOf("gachaId" to gachaId))
        val jsonArray = jsonData[typeString]
        val data: MutableList<MutableMap<String, Any>> = mutableListOf()
        jsonArray.forEach { item ->
            data.add(
                mutableMapOf(
                    "item_name" to item["item_name"],
                    "item_type" to item["item_type"]
                )
            )
        }
        return data
    }

    fun test() {
        val currentDir = File(".").absoluteFile
//        val resourcesDir = File(currentDir, "resources")
        val jsonFile = File(currentDir, "resources/gacha_up.json")

        val gachaUp = mysApi.getData("gacha_Id")["data"]["list"]

        val gachaUpMap: MutableMap<String, Map<String, Any>> = mutableMapOf(
            "常驻" to mutableMapOf(
                "begin_time" to gachaUp[0]["begin_time"].textValue(),
                "end_time" to gachaUp[0]["end_time"].textValue(),
                "gacha_id" to gachaUp[0]["gacha_id"].textValue(),
                "r5_prob_list" to test2(gachaUp[0]["gacha_id"].textValue(), "prob")
            ),
            "角色活动" to mutableMapOf(
                "begin_time" to gachaUp[1]["begin_time"].textValue(),
                "end_time" to gachaUp[1]["end_time"].textValue(),
                "gacha_id" to gachaUp[1]["gacha_id"].textValue(),
                "r5_up_items" to test2(gachaUp[1]["gacha_id"].textValue(), "up")
            ),
            "角色活动-2" to mutableMapOf(
                "begin_time" to gachaUp[2]["begin_time"].textValue(),
                "end_time" to gachaUp[2]["end_time"].textValue(),
                "gacha_id" to gachaUp[2]["gacha_id"].textValue(),
                "r5_up_items" to test2(gachaUp[2]["gacha_id"].textValue(), "up")
            ),
            "武器活动" to mutableMapOf(
                "begin_time" to gachaUp[3]["begin_time"].textValue(),
                "end_time" to gachaUp[3]["end_time"].textValue(),
                "gacha_id" to gachaUp[3]["gacha_id"].textValue(),
                "r5_up_items" to test2(gachaUp[3]["gacha_id"].textValue(), "up")
            ),
        )


        val objectMapper = ObjectMapper()
        val jsonString = objectMapper.writeValueAsString(gachaUpMap)

        val outputStream = FileOutputStream(jsonFile, false)
        outputStream.write(jsonString.toByteArray())
        outputStream.close()
    }

    /**
     * 获取当期卡池数据
     */
    private fun getInfoList(): JsonNode {
        val currentDir = File(".").absoluteFile
//        val resourcesDir = File(currentDir, "resources")
        val jsonFile = File(currentDir, "resources/genshinConfig/gacha_up.json")

        if (jsonFile.exists()) {
            val gachaUp = objectMapper.readTree(jsonFile)
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
            val lastModified = Date(jsonFile.lastModified())
            val formattedDate = dateFormat.format(lastModified)
            val endDate = dateFormat.parse(gachaUp["角色活动"]["end_time"].textValue())

            if (lastModified.after(endDate)) {
                println("Last modified date is after target date.")
            } else if (lastModified.before(endDate)) {
                println("Last modified date is before target date.")
            } else {
                println("Last modified date is equal to target date.")
            }


        } else {
            println("1.json file does not exist in resources directory.")
            test()
        }

        val data = mysApi.getData("gacha_Id")
        return data["data"]["list"]
    }

    fun gachaThread(authKeyB: JsonNode, gachaId: Int) {
        // 本页最后一条数据的id
        var endId = "0"
        for (i in 1..10000) {
            val gachaData = mysApi.getData(
                "gachaLog",
                mutableMapOf(
                    "authkey" to URLEncoder.encode(authKeyB["data"]["authkey"].textValue(), "UTF-8"),
                    "size" to 20,
                    "end_id" to endId,
                    "page" to i,
                    "gacha_type" to gachaId
                )
            )
            val length = gachaData["data"]["list"].size()
            endId = gachaData["data"]["list"][length - 1]["id"].textValue()

            for (item in gachaData["data"]["list"]) {
                val uid = item["uid"].textValue()
                val type = item["gacha_type"].textValue()
                val itemName = item["name"].textValue()
                val itemType = item["item_type"].textValue()
                val rankType = item["rank_type"].textValue()
                val itemId = item["id"].textValue()
                val getTime = item["time"].textValue()
                gaChaService.insertByUid(uid, type, itemName, 0, itemType, rankType.toInt(), itemId, getTime)
            }

            Thread.sleep(500)
            if (length < 20) break
        }
    }

    fun getData(authKeyB: JsonNode) {
        val idList = listOf(301, 302, 200)
        for (id in idList) {
            gachaThread(authKeyB, id)
            println("卡池：$idList 分析完毕")
        }
        System.gc()
    }

    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "记录查询(.*)")
    fun recordQuery(bot: Bot, event: AnyMessageEvent?, matcher: Matcher?) {
        val uid = genShinService.selectByUid(matcher?.group(1) ?: "")
        if (uid == "Null") {
            bot.sendMsg(event, "Uid为空或还没有绑定，请发送 #抽卡记录 进行绑定", false)
            return
        }
        gaChaService.selectByUid(uid)
    }

    @OptIn(DelicateCoroutinesApi::class)
    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "抽卡记录")
    suspend fun getGachaLog(bot: Bot, event: AnyMessageEvent?) {
        getInfoList()
        val (outputStream, ticket) = qrLogin.makeQrCode()
        bot.sendMsg(
            event,
            "免责声明:您将通过扫码完成获取米游社sk以及ck。\n本Bot将不会保存您的登录状态。\n我方仅提供米游社查询及相关游戏内容服务,若您的账号封禁、被盗等处罚与我方无关。\n害怕风险请勿扫码~",
            false
        )

        val sendMsg: String = MsgUtils.builder().img(WebImgUtil().outputStreamToBase64(outputStream)).build()
        bot.sendMsg(event, sendMsg, false)

        event?.let { println("it.messageId：${it.messageId}") }

        GlobalScope.launch(Dispatchers.IO) {
            delay(30000)
            println("等待完毕")
            event?.let { bot.deleteMsg(it.messageId) }
        }
        val (qrCodeStatus, checkQrCode) = qrLogin.checkQrCode(ticket)
        if (!checkQrCode) {
            bot.sendMsg(event, "二维码过期，请重新获取", false)
            return
        }
        val stoken = qrLogin.getStoken(qrCodeStatus)
        val accountInfo = qrLogin.getAccountInfo(stoken)["data"]["list"][0]
        val gameUid = accountInfo["game_uid"].textValue()
        val nickName = accountInfo["nickname"].textValue()

        mysApi = MysApi(
            gameUid,
            "mid=${stoken["data"]["user_info"]["mid"].textValue()};stoken=${stoken["data"]["token"]["token"].textValue()}"
        )

        val authKeyB = mysApi.getData("authKeyB")
        getData(authKeyB)
        genShinService.insertByUid(uid = gameUid, nickName = nickName)
    }
}