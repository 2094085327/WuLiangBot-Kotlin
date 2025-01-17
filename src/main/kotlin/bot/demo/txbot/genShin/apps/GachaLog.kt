package bot.demo.txbot.genShin.apps

import bot.demo.txbot.common.botUtil.BotUtils.Context
import bot.demo.txbot.common.database.user.UserService
import bot.demo.txbot.common.logAop.SystemLog
import bot.demo.txbot.common.utils.LoggerUtils.logError
import bot.demo.txbot.common.utils.LoggerUtils.logInfo
import bot.demo.txbot.common.utils.OtherUtil.GensokyoUtil.getRealId
import bot.demo.txbot.common.utils.WebImgUtil
import bot.demo.txbot.genShin.database.gachaLog.GaChaLogService
import bot.demo.txbot.genShin.genshinResp.GenshinRespBean
import bot.demo.txbot.genShin.genshinResp.GenshinRespEnum
import bot.demo.txbot.genShin.util.*
import bot.demo.txbot.other.distribute.annotation.AParameter
import bot.demo.txbot.other.distribute.annotation.ActionService
import bot.demo.txbot.other.distribute.annotation.Executor
import com.fasterxml.jackson.databind.JsonNode
import com.mikuac.shiro.common.utils.MsgUtils
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
@Component
@ActionService
class GachaLog(
    @Autowired private var gaChaLogService: GaChaLogService,
    @Autowired private var userService: UserService,
    @Autowired private val webImgUtil: WebImgUtil,
    @Autowired private val mysApiTools: MysApiTools,
    private val gachaLogUtil: GachaLogUtil,
    private val qrLogin: QRLogin
) {
    val updateGachaResources = UpdateGachaResources()

//    fun getData(authKeyB: JsonNode) {
//        val idList = listOf(301, 302, 200, 500)
//        for (id in idList) {
//            gachaThread(authKeyB, id)
//            logInfo("卡池：$id 分析完毕")
//        }
//        System.gc()
//    }
//
//    fun getData(url: String) {
//        val idList = listOf(301, 302, 200, 500)
//        for (id in idList) {
//            gachaThread(url, id)
//            logInfo("卡池：$id 分析完毕")
//        }
//        System.gc()
//    }

//    fun getData(authKeyB: JsonNode) = processGachaData(authKeyB) { authKeyB, id -> gachaThreadByAuth(authKeyB, id) }
//
//    fun getData(url: String) = processGachaData(url) { url, id -> gachaThreadByUrl(url, id) }
//
//    private fun processGachaData(data: Any, threadFunc: (Any, Int) -> Unit) {
//        val idList = listOf(301, 302, 200, 500)
//        idList.forEach { id ->
//            threadFunc(data, id)
//            logInfo("卡池：$id 分析完毕")
//        }
//        System.gc()
//    }

    fun getData(authKeyB: JsonNode) =
        processGachaData(authKeyB) { authKey, id -> gachaThreadByAuth(authKey as JsonNode, id) }

    fun getData(gachaUrl: String) = processGachaData(gachaUrl) { url, id -> gachaThreadByUrl(url as String, id) }

    private fun processGachaData(data: Any, threadFunc: (Any, Int) -> Unit) {
        val idList = listOf(301, 302, 200, 500)
        idList.forEach { id ->
            threadFunc(data, id)
            logInfo("卡池：$id 分析完毕")
        }
        System.gc()
    }


//    fun gachaThreadByAuth(authKeyB: JsonNode, gachaId: Int) = fetchData(authKeyB, gachaId) { map ->
//        mysApi.getData("gachaLog", map)
//    }
//
//    fun gachaThreadByUrl(gachaUrl: String, gachaId: Int) = fetchData(gachaUrl, gachaId) { map ->
//        gachaLogUtil.getDataByUrl(map["url"]!! as String)
//    }
//
//    private fun <T> fetchData(data: T, gachaId: Int, fetchFunc: (MutableMap<*,*>) -> JsonNode) {
//        var endId = "0"
//        for (i in 1..10000) {
//            val map = when (data) {
//                is JsonNode -> mutableMapOf(
//                    "authkey" to URLEncoder.encode(data["data"]["authkey"].textValue(), "UTF-8"),
//                    "size" to "20",
//                    "end_id" to endId,
//                    "page" to i,
//                    "gacha_type" to gachaId.toString()
//                )
//
//                is String -> mutableMapOf(
//                    "url" to gachaLogUtil.getUrl(url = data, gachaType = gachaId.toString(), times = i, endId = endId)
//                )
//
//                else -> throw IllegalArgumentException("不支持的数据格式")
//            } as MutableMap<*, *>
//            val gachaData = fetchFunc(map)
//            if (gachaData["data"]["list"].isEmpty) break
//            endId = gachaData["data"]["list"].last()["id"].textValue()
//            if (!gaChaLogService.insertByJson(gachaData["data"])) break
//
//            Thread.sleep(500)
//        }
//    }

    /**
     * 获取抽卡数据进程
     *
     * @param authKeyB 米游社验证
     * @param gachaId 卡池类型
     */
    fun gachaThreadByAuth(authKeyB: JsonNode, gachaId: Int) = fetchData(authKeyB, gachaId) { map ->
        mysApiTools.getData("gachaLog", map as MutableMap<String, Any?>)
    }

    /**
     * 获取抽卡数据进程
     *
     * @param gachaUrl 抽卡链接
     * @param gachaId 卡池类型
     */
    fun gachaThreadByUrl(gachaUrl: String, gachaId: Int) = fetchData(gachaUrl, gachaId) { map ->
        gachaLogUtil.getDataByUrl(map["url"]!! as String)
    }

    private fun <T> fetchData(data: T, gachaId: Int, fetchFunc: (Map<String, Any?>) -> JsonNode) {
        var endId = "0"
        for (i in 1..10000) {
            val map: Map<String, Any> = when (data) {
                is JsonNode -> mapOf(
                    "authkey" to URLEncoder.encode(data["data"]["authkey"].textValue(), "UTF-8"),
                    "size" to "20",
                    "end_id" to endId,
                    "page" to i,
                    "gacha_type" to gachaId.toString()
                )

                is String -> mapOf(
                    "url" to gachaLogUtil.getUrl(url = data, gachaType = gachaId.toString(), times = i, endId = endId)
                )

                else -> throw IllegalArgumentException("不支持的数据格式")
            }

            val gachaData = fetchFunc(map)
            if (gachaData["data"]["list"].isEmpty) break
            endId = gachaData["data"]["list"].last()["id"].textValue()
            if (!gaChaLogService.insertByJson(gachaData["data"])) break

            Thread.sleep(500)
        }
    }


    //
//
//    /**
//     * 获取抽卡数据进程
//     *
//     * @param authKeyB 用户凭证
//     * @param gachaId 卡池类型
//     */
//    fun gachaThread(authKeyB: JsonNode, gachaId: Int) {
//        // 本页最后一条数据的id
//        var endId = "0"
//        for (i in 1..10000) {
//            val gachaData = mysApi.getData(
//                "gachaLog",
//                mutableMapOf(
//                    "authkey" to URLEncoder.encode(authKeyB["data"]["authkey"].textValue(), "UTF-8"),
//                    "size" to 20,
//                    "end_id" to endId,
//                    "page" to i,
//                    "gacha_type" to gachaId
//                )
//            )
//            if (gachaData["data"]["list"].size() == 0) break
//            endId = gachaData["data"]["list"].last()["id"].textValue()
//            if (!gaChaLogService.insertByJson(gachaData["data"])) break
//
//            Thread.sleep(500)
//        }
//    }
//
//
//    /**
//     * 获取抽卡数据进程
//     *
//     * @param gachaUrl 抽卡链接
//     * @param gachaId 卡池类型
//     */
//    fun gachaThread(gachaUrl: String, gachaId: Int) {
//        var endId = "0"
//        for (i in 1..10000) {
//            val nowGachaUrl =
//                gachaLogUtil.getUrl(url = gachaUrl, gachaType = gachaId.toString(), times = i, endId = endId)
//            val gachaData = gachaLogUtil.getDataByUrl(nowGachaUrl)
//            if (gachaData["data"]["list"].size() != 0) {
//                endId = gachaData["data"]["list"].last()["id"].textValue()
//            } else return
//            if (!gaChaLogService.insertByJson(gachaData["data"])) break
//
//            Thread.sleep(500)
//        }
//    }

    @SystemLog(businessName = "获取当前用户抽卡历史记录")
    @AParameter
    @Executor(action = "历史记录(.*)")
    fun recordQuery(context: Context, matcher: Matcher) {
        context.sendMsg(GenshinRespEnum.SEARCH_HISTORY.message)
        updateGachaResources.getDataMain()
        val realId = context.getEvent().getRealId()
        val gameUidFromMatcher = matcher.group(1)?.replace(" ", "")
        val gameUid = gameUidFromMatcher.takeIf { it?.isNotEmpty() == true }
            ?: userService.selectGenUidByRealId(realId)

        if (gameUid.isNullOrEmpty()) {
            context.sendMsg(GenshinRespEnum.BIND_NOTFOUND.message)
            return
        }

        val imageData = WebImgUtil.ImgData(
            imgName = "gachaLog-$gameUid",
            url = "http://localhost:${webImgUtil.usePort}/gachaLog"
        )

        val result = gachaLogUtil.checkCache(imageData) ?: gaChaLogService.selectByUid(gameUid)

        result?.let {
            gachaLogUtil.getGachaLog(context, gameUid, imageData)
        }
        System.gc()
    }

    @SystemLog(businessName = "删除保存的历史记录")
    @AParameter
    @Executor(action = "删除记录")
    fun deleteGachaLog(context: Context) {
        val realId = context.getEvent().getRealId()
        val gameUid = userService.selectGenUidByRealId(realId)

        if (gameUid.isNullOrEmpty()) {
            context.sendMsg(GenshinRespEnum.BIND_NOTFOUND.message)
            return
        }

        val folder = File(GACHA_CACHE_PATH)
        val prefix = "gachaLog-$gameUid"

        folder.listFiles { _, name -> name.startsWith(prefix) }?.firstOrNull()?.let { file ->
            val deleted = file.delete()
            val message =
                if (deleted) "抽卡记录${prefix}删除成功" else "抽卡记录${prefix}删除失败，记录可能正在使用，请稍后再试"
            context.sendMsg(message)
        } ?: context.sendMsg(GenshinRespEnum.NO_USER_RECORD.message)
    }

    @SystemLog(businessName = "通过二维码获取抽卡记录")
    @OptIn(DelicateCoroutinesApi::class)
    @AParameter
    @Executor(action = "\\b抽卡记录\\b")
    fun getGachaLog(context: Context) {
        val realId = context.getEvent().getRealId()
        MysApiTools.deviceId = gachaLogUtil.convertStringToUuidFormat(realId)
        val (outputStream, ticket) = qrLogin.makeQrCode()

        context.sendMsg(GenshinRespEnum.DISCLAIMER.message)
        val sendMsg = MsgUtils.builder().img(webImgUtil.outputStreamToBase64(outputStream)).build()
        val qrImageMsg = context.sendMsg(sendMsg)

        updateGachaResources.getDataMain()

        GlobalScope.launch(Dispatchers.IO) {
            delay(30000)
            logInfo("撤回二维码")
            if (qrImageMsg != null) {
                context.deleteMsg(qrImageMsg.data.messageId)
            }
        }
        val (qrCodeStatus, checkQrCode) = qrLogin.checkQrCode(ticket)
        if (!checkQrCode) {
            context.sendMsg(qrCodeStatus["message"].textValue())
            return
        }
        context.sendMsg(GenshinRespEnum.LOGIN_SUCCESS.message)

        MysDataUtil().deleteDataCache()
        val stoken = qrLogin.getStoken(qrCodeStatus)
        // TODO 之后修改为不为正确状态码即返回
        if (stoken["retcode"].intValue() == -100) {
            context.sendMsg("${stoken["message"].textValue()} 若多次出现此提示请尝试使用「抽卡链接」获取链接后进行分析")
            return
        }
        qrLogin.getAccountInfo(stoken).let { accountInfo ->
            val accountInfoDetail = accountInfo["data"]["list"][0]
            val gameUid = accountInfoDetail["game_uid"].textValue()

            MysApiTools.uid = gameUid
            MysApiTools.cookie =
                "mid=${stoken["data"]["user_info"]["mid"].textValue()};stoken=${stoken["data"]["token"]["token"].textValue()}"

            val authKeyB = mysApiTools.getData("authKeyB")
            getData(authKeyB)
            gaChaLogService.selectByUid(gameUid)
            val imageData = WebImgUtil.ImgData(
                imgName = "gachaLog-$gameUid",
                url = "http://localhost:${webImgUtil.usePort}/gachaLog",
                openCache = false
            )
            gachaLogUtil.getGachaLog(context, gameUid, imageData)
            userService.insertGenUidByRealId(realId, gameUid)
        }

    }

    @SystemLog(businessName = "向机器人发送抽卡连接获取抽卡记录")
    @AParameter
    @Executor(action = "抽卡记录\\s*(\\S.*)")
    fun getGachaLogByUrlGroup(context: Context, matcher: Matcher) {
        if ((context.getEvent() as AnyMessageEvent).groupId != null) {
            context.sendMsg(GenshinRespEnum.SEND_LINK_FAIL.message)
            return
        }

        // 获取到的链接会被自动将&转义为&amp;，需要替换回来
        val gachaUrl = matcher.group(1)?.replace(" ", "")?.replace("&amp;", "&") ?: ""

        if (gachaUrl.isEmpty()) return

        context.sendMsg(GenshinRespEnum.GET_LINK.message)

        // 更新卡池数据
        updateGachaResources.getDataMain()

        val processingUrl = gachaLogUtil.toUrl(gachaUrl)
        val checkUrl = gachaLogUtil.checkApi(processingUrl)
        when (checkUrl.first) {
            "-100" -> {
                context.sendMsg(GenshinRespEnum.LINK_INCOMPLETE.message)
                return
            }

            "-101" -> {
                context.sendMsg(GenshinRespEnum.LINK_EXPIRED.message)
                return
            }

            "0" -> {
                context.sendMsg(GenshinRespEnum.LINK_SUCCESS.message)
                val realId = context.getEvent().getRealId()
                MysApiTools.deviceId = gachaLogUtil.convertStringToUuidFormat(realId)

                getData(processingUrl)
                val gameUid = checkUrl.second!!
                gaChaLogService.selectByUid(gameUid)
                val imageData = WebImgUtil.ImgData(
                    imgName = "gachaLog-${gameUid}",
                    url = "http://localhost:${webImgUtil.usePort}/gachaLog",
                    openCache = false
                )
                webImgUtil.deleteImg(imageData)
                gachaLogUtil.getGachaLog(context, gameUid, imageData)
                userService.insertGenUidByRealId(realId, gameUid)
            }

            else -> context.sendMsg(GenshinRespEnum.LINK_FAIL.message)
        }
    }


    /**
     * 导入抽卡记录
     *
     * @return 导入状态
     */
    fun importGachaLog(): GenshinRespBean {
        try {
            val folder = File(GACHA_LOG_IMPORT)
            if (!folder.exists()) {
                folder.mkdirs()
            }
            val foldList = folder.listFiles()!!
            if (foldList.isEmpty()) {
                return GenshinRespBean.error(GenshinRespEnum.IMPORT_HISTORY_EMPTY)
            }

            // 遍历文件
            foldList.forEach { file ->
                if (file.name.endsWith(".json")) {
                    val gachaData = MysDataUtil().getGachaData("$GACHA_LOG_IMPORT/${file.name}")
                    gaChaLogService.insertByJson(gachaData)
                    logInfo(GenshinRespEnum.IMPORT_HISTORY_SUCCESS.message)
                    file.delete()
                }
            }
            return GenshinRespBean.error(GenshinRespEnum.IMPORT_HISTORY_SUCCESS)
        } catch (e: Exception) {
            e.printStackTrace()
            logError("读取gachaLog失败:$e")
            return GenshinRespBean.error(GenshinRespEnum.IMPORT_HISTORY_FAIL)
        }
    }

    @SystemLog(businessName = "导入通用抽卡记录数据")
    @AParameter
    @Executor(action = "导入记录")
    fun getGachaLogByUrlGroup(context: Context) {
        context.sendMsg(GenshinRespEnum.IMPORT_HISTORY.message)
        val importState = importGachaLog()
        context.sendMsg(importState.message)
    }

    @SystemLog(businessName = "获取抽卡链接指令")
    @AParameter
    @Executor(action = "抽卡链接")
    fun getGachaLogUrl(context: Context) {
        context.sendMsg("pause;${'$'}m=(((Get-Clipboard -TextFormatType Html) | sls \"(https:/.+log)\").Matches[0].Value);${'$'}m;Set-Clipboard -Value ${'$'}m")
    }
}