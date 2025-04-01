package bot.wuliang.controller

import bot.wuliang.botLog.logAop.SystemLog
import bot.wuliang.botLog.logUtil.LoggerUtils.logError
import bot.wuliang.botLog.logUtil.LoggerUtils.logInfo
import bot.wuliang.botUtil.BotUtils
import bot.wuliang.botUtil.GensokyoUtil.getRealId
import bot.wuliang.config.GACHA_CACHE_PATH
import bot.wuliang.config.GACHA_LOG_IMPORT
import bot.wuliang.distribute.annotation.AParameter
import bot.wuliang.distribute.annotation.ActionService
import bot.wuliang.distribute.annotation.Executor
import bot.wuliang.event.ResourceUpdateEvent
import bot.wuliang.exception.RespBean
import bot.wuliang.imageProcess.WebImgUtil
import bot.wuliang.respEnum.GenshinRespEnum
import bot.wuliang.service.GaChaLogService
import bot.wuliang.user.service.UserService
import bot.wuliang.utils.*
import com.mikuac.shiro.common.utils.MsgUtils
import com.mikuac.shiro.dto.event.message.AnyMessageEvent
import kotlinx.coroutines.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.RequestMapping
import java.io.File
import java.util.regex.Matcher


/**
 *@Description:
 *@Author zeng
 *@Date 2023/9/8 17:01
 *@User 86188
 */
@Component
@ActionService
class GachaLogController {
    @Autowired
    private lateinit var gaChaLogService: GaChaLogService

    @Autowired
    private lateinit var userService: UserService

    @Autowired
    private lateinit var webImgUtil: WebImgUtil

    @Autowired
    private lateinit var gachaLogUtil: GachaLogUtil

    @Autowired
    private lateinit var qrLogin: QRLoginUtil

    @Autowired
    private lateinit var mysApiTools: MysApiTools

    val updateGachaResources = UpdateGachaResources()


    @SystemLog(businessName = "获取当前用户抽卡历史记录")
    @AParameter
    @Executor(action = "历史记录(.*)")
    fun recordQuery(context: BotUtils.Context, matcher: Matcher) {
        context.sendMsg(GenshinRespEnum.SEARCH_HISTORY.message)
        updateGachaResources.getDataMain(ResourceUpdateEvent(this))
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
    fun deleteGachaLog(context: BotUtils.Context) {
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
    fun getGachaLog(context: BotUtils.Context) {
        val realId = context.getEvent().getRealId()
        MysApiTools.deviceId = gachaLogUtil.convertStringToUuidFormat(realId)
        val (outputStream, ticket) = qrLogin.makeQrCode()

        context.sendMsg(GenshinRespEnum.DISCLAIMER.message)
        val sendMsg = MsgUtils.builder().img(webImgUtil.outputStreamToBase64(outputStream)).build()
        val qrImageMsg = context.sendMsg(sendMsg)

        updateGachaResources.getDataMain(ResourceUpdateEvent(this))

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
            gachaLogUtil.getData(authKeyB)
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
    fun getGachaLogByUrlGroup(context: BotUtils.Context, matcher: Matcher) {
        if ((context.getEvent() as AnyMessageEvent).groupId != null) {
            context.sendMsg(GenshinRespEnum.SEND_LINK_FAIL.message)
            return
        }

        // 获取到的链接会被自动将&转义为&amp;，需要替换回来
        val gachaUrl = matcher.group(1)?.replace(" ", "")?.replace("&amp;", "&") ?: ""

        if (gachaUrl.isEmpty()) return

        context.sendMsg(GenshinRespEnum.GET_LINK.message)

        // 更新卡池数据
        updateGachaResources.getDataMain(ResourceUpdateEvent(this))

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

                gachaLogUtil.getData(processingUrl)
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
    fun importGachaLog(): RespBean {
        try {
            val folder = File(GACHA_LOG_IMPORT)
            if (!folder.exists()) {
                folder.mkdirs()
            }
            val foldList = folder.listFiles()!!
            if (foldList.isEmpty()) {
                return RespBean.error(GenshinRespEnum.IMPORT_HISTORY_EMPTY)
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
            return RespBean.error(GenshinRespEnum.IMPORT_HISTORY_SUCCESS)
        } catch (e: Exception) {
            e.printStackTrace()
            logError("读取gachaLog失败:$e")
            return RespBean.error(GenshinRespEnum.IMPORT_HISTORY_FAIL)
        }
    }

    @SystemLog(businessName = "导入通用抽卡记录数据")
    @AParameter
    @Executor(action = "导入记录")
    fun getGachaLogByUrlGroup(context: BotUtils.Context) {
        context.sendMsg(GenshinRespEnum.IMPORT_HISTORY.message)
        val importState = importGachaLog()
        context.sendMsg(importState.message!!)
    }

    @SystemLog(businessName = "获取抽卡链接指令")
    @AParameter
    @Executor(action = "抽卡链接")
    fun getGachaLogUrl(context: BotUtils.Context) {
        context.sendMsg("pause;${'$'}m=(((Get-Clipboard -TextFormatType Html) | sls \"(https:/.+log)\").Matches[0].Value);${'$'}m;Set-Clipboard -Value ${'$'}m")
    }

    @RequestMapping("/gachaLog")
    fun gacha(model: Model): String {
        val gachaData = gachaLogUtil.getGachaData()
        val permanents = gachaData.permanents
        val roles = gachaData.roles
        val weapons = gachaData.weapons
        val mixPools = gachaData.mixPool

        val roleCount = gachaData.roleCount
        val weaponCount = gachaData.weaponCount
        val permanentCount = gachaData.permanentCount
        val mixPoolCount = gachaData.mixCount

        model.addAttribute("permanents", permanents)
        model.addAttribute("roles", roles)
        model.addAttribute("weapons", weapons)
        model.addAttribute("mixPools", mixPools)
        model.addAttribute("roleCount", roleCount)
        model.addAttribute("weaponCount", weaponCount)
        model.addAttribute("permanentCount", permanentCount)
        model.addAttribute("mixPoolCount", mixPoolCount)
        return "GenShin/GachaLog"
    }
}