package bot.demo.txbot.genShin.apps

import bot.demo.txbot.common.utils.WebImgUtil
import bot.demo.txbot.genShin.database.gachaLog.GaChaLogService
import bot.demo.txbot.genShin.database.genshin.GenShinService
import bot.demo.txbot.genShin.util.MysApi
import bot.demo.txbot.genShin.util.MysDataUtil
import com.fasterxml.jackson.databind.JsonNode
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
import java.net.URLEncoder
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
    lateinit var gaChaLogService: GaChaLogService

    @Autowired
    lateinit var genShinService: GenShinService

    @Autowired
    val webImgUtil = WebImgUtil()

    private val qrLogin = QRLogin()
    private var mysApi = MysApi("0", "")

    fun getData(authKeyB: JsonNode) {
        val idList = listOf(301, 302, 200)
        for (id in idList) {
            gachaThread(authKeyB, id)
            println("卡池：$id 分析完毕")
        }
        System.gc()
    }

    /**
     * 获取抽卡数据进程
     *
     * @param authKeyB 用户凭证
     * @param gachaId 卡池类型
     */
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
            if (length == 0) break
            endId = gachaData["data"]["list"][length - 1]["id"].textValue()

            for (item in gachaData["data"]["list"]) {
                val uid = item["uid"].textValue()
                val type = item["gacha_type"].textValue()
                val itemName = item["name"].textValue()
                val itemType = item["item_type"].textValue()
                val rankType = item["rank_type"].textValue()
                val itemId = item["id"].textValue()
                val getTime = item["time"].textValue()
                gaChaLogService.insertByUid(uid, type, itemName, itemType, rankType.toInt(), itemId, getTime)
            }

            Thread.sleep(500)
        }
    }

    /**
     * 发送非缓存截图
     *
     * @param bot 机器人
     * @param event 事件
     * @param imgName 图片名
     * @param webUrl 待截图地址
     * @param scale 缩放等级
     */
    private fun sendNewImage(
        bot: Bot,
        event: AnyMessageEvent?,
        imgName: String,
        webUrl: String,
        scale: Double? = null
    ) {
        val imgUrl =
            webImgUtil.getImgFromWeb(url = webUrl, imgName = imgName, element = "body", channel = true, scale = scale)
        val sendMsg: String = MsgUtils.builder().img(imgUrl).build()
        bot.sendMsg(event, sendMsg, false)
        bot.sendMsg(event, "发送完毕", false)
    }

    /**
     * 获取抽卡记录
     *
     * @param bot 机器人
     * @param event 事件
     * @param gameUid 游戏Uid
     * @param imgName 图片名称
     */
    fun getGachaLog(bot: Bot, event: AnyMessageEvent?, gameUid: String, imgName: String) {
        val gachaData = MysDataUtil().getGachaData("resources/gachaCache/gachaLog-$gameUid.json")
        val pools = arrayOf("200", "301", "302")
        pools.forEach { type ->
            MysDataUtil().getEachData(gachaData, type)
        }

        sendNewImage(bot, event, imgName, "http://localhost:${WebImgUtil.usePort}/gachaLog")
    }

    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "新增角色(.*)")
    fun updateNewItem(bot: Bot, event: AnyMessageEvent?, matcher: Matcher?) {
        val newItem = matcher?.group(1) ?: ""
        val newItemName = newItem.split(" ")[0]
        val newItemType = newItem.split(" ")[1]
        when (val code = MysDataUtil().insertAttribute(newItemName, newItemType)) {
            "200" -> bot.sendMsg(event, "角色更新成功", false)
            "201" -> bot.sendMsg(event, "这个角色已经存在了哦", false)
            "404" -> bot.sendMsg(event, "属性中没有 $newItemType 类型的属性哦", false)
            else -> bot.sendMsg(event, "未知错误: $code，请联系管理员查看", false)
        }
    }

    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "清除缓存")
    fun deleteCache(bot: Bot, event: AnyMessageEvent?, matcher: Matcher?) {
        MysDataUtil().forceDeleteCache("resources/gachaCache")
        MysDataUtil().forceDeleteCache("resources/imageCache")
        bot.sendMsg(event, "已完成缓存清理", false)
    }

    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "记录查询(.*)")
    fun recordQuery(bot: Bot, event: AnyMessageEvent?, matcher: Matcher?) {
        val gameUid = matcher?.group(1)?.replace(" ", "") ?: ""

        if (gameUid.isEmpty()) {
            bot.sendMsg(event, "请使用 记录查询<你的Uid> 进行查询", false)
            return
        }

        bot.sendMsg(event, "正在查询历史数据，请稍等", false)

        MysDataUtil().deleteDataCache()
        val imgName = "gachaLog-${gameUid}"
        val folder = File(MysDataUtil.CACHE_PATH)
        val cacheImg = File("resources/imageCache")

        val matchingFile = folder.listFiles()?.firstOrNull { it.nameWithoutExtension == imgName }
        val matchCache = cacheImg.listFiles()?.firstOrNull { it.nameWithoutExtension == imgName }

        if (matchingFile != null) {
            if (matchCache != null) {
                webImgUtil.sendCachedImage(bot, event, imgName, matchCache)

            } else {
                getGachaLog(bot, event, gameUid, imgName)
            }


        } else {
            val result = gaChaLogService.selectByUid(gameUid)

            if (result == null) {
                bot.sendMsg(event, "Uid为空或还没有绑定，请发送 #抽卡记录 进行绑定", false)
                return
            }
            getGachaLog(bot, event, gameUid, imgName)

        }

        System.gc()
    }


    @OptIn(DelicateCoroutinesApi::class)
    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "抽卡记录")
    suspend fun getGachaLog(bot: Bot, event: AnyMessageEvent?) {
        val (outputStream, ticket) = qrLogin.makeQrCode()
        bot.sendMsg(
            event,
            "免责声明:您将通过扫码完成获取米游社sk以及ck。\n本Bot将不会保存您的登录状态。\n我方仅提供米游社查询及相关游戏内容服务,若您的账号封禁、被盗等处罚与我方无关。\n害怕风险请勿扫码~",
            false
        )

        val sendMsg: String = MsgUtils.builder().img(webImgUtil.outputStreamToBase64(outputStream)).build()
        bot.sendMsg(event, sendMsg, false)

        GlobalScope.launch(Dispatchers.IO) {
            delay(30000)
            println("等待完毕")
            event?.let { bot.deleteMsg(it.messageId) }
        }
        val (qrCodeStatus, checkQrCode) = qrLogin.checkQrCode(ticket)
        if (!checkQrCode) {
            bot.sendMsg(event, "二维码过期，请重新获取", false)
            return
        } else {
            bot.sendMsg(event, "登录成功,正在获取抽卡数据，时间根据抽卡次数不同需花费30秒至1分钟不等，请耐心等待", false)
        }
        // 删除缓存
        MysDataUtil().deleteDataCache()
        // 获取临时stoken
        val stoken = qrLogin.getStoken(qrCodeStatus)
        val accountInfo = qrLogin.getAccountInfo(stoken)["data"]["list"][0]
        val gameUid = accountInfo["game_uid"].textValue()

        // 发起抽卡数据请求
        mysApi = MysApi(
            gameUid,
            "mid=${stoken["data"]["user_info"]["mid"].textValue()};stoken=${stoken["data"]["token"]["token"].textValue()}"
        )

        val authKeyB = mysApi.getData("authKeyB")
        getData(authKeyB)
        gaChaLogService.selectByUid(gameUid)
        getGachaLog(bot, event, gameUid, "gachaLog-${gameUid}")
    }
}