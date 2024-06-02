package bot.demo.txbot.genShin.apps

import bot.demo.txbot.common.database.user.UserService
import bot.demo.txbot.common.utils.OtherUtil
import bot.demo.txbot.common.utils.WebImgUtil
import bot.demo.txbot.genShin.database.gachaLog.GaChaLogService
import bot.demo.txbot.genShin.util.*
import com.fasterxml.jackson.databind.JsonNode
import com.mikuac.shiro.annotation.AnyMessageHandler
import com.mikuac.shiro.annotation.GroupMessageHandler
import com.mikuac.shiro.annotation.MessageHandlerFilter
import com.mikuac.shiro.annotation.PrivateMessageHandler
import com.mikuac.shiro.annotation.common.Shiro
import com.mikuac.shiro.common.utils.MsgUtils
import com.mikuac.shiro.core.Bot
import com.mikuac.shiro.dto.event.message.AnyMessageEvent
import com.mikuac.shiro.dto.event.message.GroupMessageEvent
import com.mikuac.shiro.dto.event.message.PrivateMessageEvent
import kotlinx.coroutines.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import pers.wuliang.robot.common.utils.LoggerUtils.logError
import pers.wuliang.robot.common.utils.LoggerUtils.logInfo
import pers.wuliang.robot.common.utils.LoggerUtils.logWarn
import java.io.File
import java.net.URLEncoder
import java.util.logging.Logger
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
    lateinit var userService: UserService

    @Autowired
    val webImgUtil = WebImgUtil()

    val gachaLogUtil = GachaLogUtil()

    private val qrLogin = QRLogin()
    private var mysApi = MysApi("0", "")
    private val logger: Logger = Logger.getLogger(GachaLog::class.java.getName())


    fun getData(authKeyB: JsonNode) {
        val idList = listOf(301, 302, 200, 500)
        for (id in idList) {
            gachaThread(authKeyB, id)
            logger.info("卡池：$id 分析完毕")
        }
        System.gc()
    }

    fun getData(url: String) {
        val idList = listOf(301, 302, 200, 500)
        for (id in idList) {
            gachaThread(url, id)
            logger.info("卡池：$id 分析完毕")
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
            if (gachaData["data"]["list"].size() == 0) break
            endId = gachaData["data"]["list"].last()["id"].textValue()
            if (!gaChaLogService.insertByJson(gachaData["data"])) break

            Thread.sleep(500)
        }
    }


    /**
     * 获取抽卡数据进程
     *
     * @param gachaUrl 抽卡链接
     * @param gachaId 卡池类型
     */
    fun gachaThread(gachaUrl: String, gachaId: Int) {
        var endId = "0"
        for (i in 1..10000) {
            val nowGachaUrl =
                gachaLogUtil.getUrl(url = gachaUrl, gachaType = gachaId.toString(), times = i, endId = endId)
            val gachaData = gachaLogUtil.getDataByUrl(nowGachaUrl)
            if (gachaData["data"]["list"].size() != 0) {
                endId = gachaData["data"]["list"].last()["id"].textValue()
            } else return
            if (!gaChaLogService.insertByJson(gachaData["data"])) break

            Thread.sleep(500)
        }
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
    @MessageHandlerFilter(cmd = "历史记录")
    fun recordQueryByRealId(bot: Bot, event: AnyMessageEvent) {
        bot.sendMsg(event, "正在查询历史数据，请稍等", false)
        val realId = OtherUtil().getRealId(event)
        val gameUid = userService.selectGenUidByRealId(realId)
        if (gameUid == null) {
            bot.sendMsg(event, "Uid还没有绑定，请发送 抽卡记录 进行绑定", false)
            return
        }

        val imgName = "gachaLog-${gameUid}"

        val checkResult = gachaLogUtil.checkCache(imgName, gameUid)

        if (checkResult.first != null) {
            if (checkResult.second != null) {
                webImgUtil.sendCachedImage(bot, event, checkResult.second!!)
            } else {
                gachaLogUtil.getGachaLog(bot, event, gameUid, imgName)
            }
        } else {
            gaChaLogService.selectByUid(gameUid) ?: return
            gachaLogUtil.getGachaLog(bot, event, gameUid, imgName)
        }

        System.gc()
    }

    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "历史记录(.*)")
    fun recordQuery(bot: Bot, event: AnyMessageEvent, matcher: Matcher) {
        val gameUid = matcher.group(1)?.replace(" ", "") ?: ""

        if (gameUid.isEmpty()) {
            return
        }

        bot.sendMsg(event, "正在查询历史数据，请稍等", false)

        val imgName = "gachaLog-${gameUid}"

        val checkResult = gachaLogUtil.checkCache(imgName, gameUid)

        if (checkResult.first != null) {
            if (checkResult.second != null) {
                webImgUtil.sendCachedImage(bot, event, checkResult.second!!)

            } else {
                gachaLogUtil.getGachaLog(bot, event, gameUid, imgName)
            }


        } else {
            val result = gaChaLogService.selectByUid(gameUid)

            if (result == null) {
                bot.sendMsg(event, "Uid为空或还没有绑定，请发送 抽卡记录 进行绑定", false)
                return
            }
            gachaLogUtil.getGachaLog(bot, event, gameUid, imgName)
        }

        System.gc()
    }

    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "删除记录")
    fun deleteGachaLog(bot: Bot, event: AnyMessageEvent, matcher: Matcher) {
        val realId = OtherUtil().getRealId(event)
        val gameUid = userService.selectGenUidByRealId(realId)
        if (gameUid == null) {
            bot.sendMsg(event, "Uid还没有绑定，请发送 抽卡记录 进行绑定", false)
            return
        }

        val folder = File(GACHA_CACHE_PATH)
        val prefix = "gachaLog-${gameUid}"

        val files = folder.listFiles { _, name -> name.startsWith(prefix) }
        if (files != null && files.isNotEmpty()) {
            val fileToDelete = files.first()
            val deleted = fileToDelete.delete()
            if (deleted) {
                bot.sendMsg(event, "抽卡记录${prefix}删除成功", false)
                logInfo("删除文件: ${fileToDelete.name}")
            } else {
                bot.sendMsg(event, "抽卡记录${prefix}删除失败，记录可能正在使用，请稍后再试", false)
                logWarn("删除文件: ${fileToDelete.name} 失败")
            }
        } else bot.sendMsg(event, "当前账户下无抽卡记录", false)
    }


    @OptIn(DelicateCoroutinesApi::class)
    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "抽卡记录")
    suspend fun getGachaLog(bot: Bot, event: AnyMessageEvent) {
        val (outputStream, ticket) = qrLogin.makeQrCode()
        bot.sendMsg(
            event,
            "免责声明:您将通过扫码完成获取米游社sk以及ck。\n本Bot将不会保存您的登录状态。\n我方仅提供米游社查询及相关游戏内容服务,若您的账号封禁、被盗等处罚与我方无关。\n害怕风险请勿扫码~",
            false
        )

        val sendMsg: String = MsgUtils.builder().img(webImgUtil.outputStreamToBase64(outputStream)).build()
        val qrImageMsg = bot.sendMsg(event, sendMsg, false)

        GlobalScope.launch(Dispatchers.IO) {
            delay(30000)
            logger.info("撤回二维码")
            bot.deleteMsg(event.groupId, bot.selfId, qrImageMsg.data.messageId)
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
        gachaLogUtil.getGachaLog(bot, event, gameUid, "gachaLog-${gameUid}")
        userService.insertGenUidByRealId(OtherUtil().getRealId(event), gameUid)
    }

    @GroupMessageHandler
    @MessageHandlerFilter(cmd = "抽卡记录(.*)")
    suspend fun getGachaLogByUrlGroup(bot: Bot, event: GroupMessageEvent, matcher: Matcher) {
        val gachaUrl = matcher.group(1)?.replace(" ", "")

        if (gachaUrl.isNullOrEmpty()) {
            return
        }
        bot.sendGroupMsg(event.groupId, "请通过私聊无量姬发送链接，避免信息泄露，请及时撤回消息", false)
    }

    @PrivateMessageHandler
    @MessageHandlerFilter(cmd = "抽卡记录(.*)")
    suspend fun getGachaLogByUrl(bot: Bot, event: PrivateMessageEvent, matcher: Matcher?) {
        // 获取到的链接会被自动将&转义为&amp;，需要替换回来
        val gachaUrl = matcher?.group(1)?.replace(" ", "")?.replace("&amp;", "&") ?: ""

        if (gachaUrl.isEmpty()) {
            return
        }

        bot.sendPrivateMsg(event.userId, "收到链接，正在处理中，请耐心等待", false)
        val processingUrl = gachaLogUtil.toUrl(gachaUrl)
        val checkUrl = gachaLogUtil.checkApi(processingUrl)
        when (checkUrl.first) {
            "-100" -> {
                bot.sendPrivateMsg(
                    event.userId,
                    "链接不完整，请复制全部内容（可能输入法复制限制），或者复制的不是历史记录页面链接",
                    false
                )
                return
            }

            "-101" -> {
                bot.sendPrivateMsg(event.userId, "链接已过期，请重新获取", false)
                return
            }

            "0" -> {
                bot.sendPrivateMsg(
                    event.userId,
                    "链接验证成功,正在获取抽卡数据，时间根据抽卡次数不同需花费30秒至1分钟不等，请耐心等待",
                    false
                )
                getData(processingUrl)
                val gameUid = checkUrl.second!!
                gaChaLogService.selectByUid(gameUid)
                gachaLogUtil.getGachaLog(bot, event, gameUid, "gachaLog-${gameUid}")
                userService.insertGenUidByRealId(OtherUtil().getRealId(event), gameUid)
            }

            else -> {
                bot.sendPrivateMsg(event.userId, "抽卡链接格式不正确，请仔细检查或联系管理员", false)
            }
        }
    }

    /**
     * 导入抽卡记录
     *
     * @return 导入状态
     */
    fun importGachaLog(): String {
        try {
            val folder = File(GACHA_LOG_IMPORT)
            if (!folder.exists()) {
                folder.mkdirs()
            }
            val foldList = folder.listFiles()!!
            if (foldList.isEmpty()) {
                return "empty"
            } else {
                foldList.forEach { file ->
                    if (file.name.endsWith(".json")) {
                        val gachaData = MysDataUtil().getGachaData("$GACHA_LOG_IMPORT/${file.name}")
                        println("gachaData:$gachaData")
                        gaChaLogService.insertByJson(gachaData)
                        logInfo("抽卡记录导入完成")
                        file.delete()
                    }
                }
            }
            return "success"
        } catch (e: Exception) {
            e.printStackTrace()
            logError("读取gachaLog失败:$e")
            return "false"
        }
    }


    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "导入记录")
    suspend fun importGachaLogInfo(bot: Bot, event: AnyMessageEvent) {
        val importState = importGachaLog()
        bot.sendMsg(event, "正在导入记录，请稍等", false)
        when (importState) {
            "success" -> bot.sendMsg(event, "记录导入完成", false)
            // 受腾讯机器人API限制，无法通过群聊获取文件，暂时只能由开发者手动将文件放入导入文件夹
            "empty" -> bot.sendMsg(event, "没有可以导入的记录", false)
            "false" -> bot.sendMsg(event, "记录导入失败，请检查记录格式是否符合UIGF标准", false)
        }
    }

    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "抽卡链接")
    suspend fun getGachaLogUrl(bot: Bot, event: AnyMessageEvent?, matcher: Matcher?) {
        bot.sendMsg(
            event,
            "pause;${'$'}m=(((Get-Clipboard -TextFormatType Html) | sls \"(https:/.+log)\").Matches[0].Value);${'$'}m;Set-Clipboard -Value ${'$'}m",
            false
        )
    }
}