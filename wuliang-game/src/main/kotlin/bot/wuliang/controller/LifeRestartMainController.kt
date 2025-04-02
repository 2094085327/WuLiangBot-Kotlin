package bot.wuliang.controller

import bot.wuliang.botLog.logAop.SystemLog
import bot.wuliang.botUtil.BotUtils
import bot.wuliang.botUtil.GensokyoUtil.getRealUserId
import bot.wuliang.config.TALENT_SELECT_Limit
import bot.wuliang.config.TALENT_SELECT_NOT_COMPLETE
import bot.wuliang.distribute.annotation.AParameter
import bot.wuliang.distribute.annotation.ActionService
import bot.wuliang.distribute.annotation.Executor
import bot.wuliang.imageProcess.WebImgUtil
import bot.wuliang.jacksonUtil.JacksonUtil
import bot.wuliang.redis.RedisService
import bot.wuliang.respEnum.RestartRespEnum
import bot.wuliang.service.LifeRestartService
import bot.wuliang.service.impl.LifeRestartServiceImpl
import bot.wuliang.utils.LifeRestartUtil
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.regex.Matcher


/**
 * @description: 人生重开主文件
 * @author Nature Zero
 * @date 2024/2/14 18:54
 */
@Component
@ActionService
class LifeRestartMainController @Autowired constructor(
    private val lifeRestartService: LifeRestartServiceImpl,
    private val webImgUtil: WebImgUtil,
    private val restartUtil: LifeRestartUtil,
    private val redisService: RedisService
) {

    @RequestMapping("/lifeRestart")
    fun lifeRestart(@RequestParam("game_userId") userId: String): Pair<MutableMap<String, Int>, Any?> {
        val userInfo = redisService.getValue("lifeRestart:userInfo:${userId}", bot.wuliang.entity.UserInfoEntity::class.java)
        val sendMessage = redisService.getValue("lifeRestart:sendMessage:${userId}")
        return Pair(userInfo?.property ?: mutableMapOf(), sendMessage)
    }

    @RequestMapping("/lifeRestartTalent")
    fun talent(@RequestParam("game_userId") userId: String): MutableList<bot.wuliang.entity.vo.TalentDataVo>? {
        return redisService.getValue(
            "lifeRestart:userInfo:${userId}",
            bot.wuliang.entity.UserInfoEntity::class.java
        )?.randomTalentTemp
    }


    @RequestMapping("/lifeRestartEndGame")
    fun lifeRestartEndGame(@RequestParam("game_userId") userId: String): Map<*, *> {
        return redisService.getValue("lifeRestart:endGame:${userId}") as Map<*, *>
    }

    @SystemLog(businessName = "人生重开游戏开始")
    @AParameter
    @Executor(action = "重开")
    fun startRestart(context: BotUtils.Context) {
        // 初始化游戏数据
        val fetchResp = restartUtil.getFetchData()
        if (fetchResp.code != RestartRespEnum.SUCCESS.code) {
            context.sendMsg(fetchResp.message!!)
            return
        }

        val realId = context.getEvent().getRealUserId()

        val userGameInfo = lifeRestartService.selectRestartInfoByRealId(realId)
        val userInfo = bot.wuliang.entity.UserInfoEntity(
            userId = realId,
            attributes = null,
            age = -1,
            gameTimes = userGameInfo?.times ?: 0,
            achievement = userGameInfo?.cachv ?: 0,
        )

        redisService.deleteKey("lifeRestart:sendMessage:${realId}")
        redisService.setValueWithExpiry("lifeRestart:userInfo:${realId}", userInfo, 5L, TimeUnit.MINUTES)

        // 抽取天赋
        restartUtil.talentRandomInit(userInfo = userInfo)


        val imageData = WebImgUtil.ImgData(
            imgName = "${userInfo.userId}-LifeStartTalent-${UUID.randomUUID()}",
            url = "http://localhost:16666/game/lifeRestartTalent?game_userId=${userInfo.userId}"
        )
        webImgUtil.sendNewImage(context, imageData)

        context.sendMsg(RestartRespEnum.GAME_START_SUCCESS.message)
        webImgUtil.deleteImg(imageData)
    }

    @SystemLog(businessName = "选择人生重开游戏天赋")
    @AParameter
    @Executor(action = "天赋 (.*)")
    fun getTalent(context: BotUtils.Context, matcher: Matcher) {
        val realId = context.getEvent().getRealUserId()
        val userInfo = restartUtil.findUserInfo(realId)

        val errorState = restartUtil.errorState(userInfo, LifeRestartUtil.OperationType.CHOOSE_TALENT)
        if (errorState.code != RestartRespEnum.SUCCESS.code) {
            context.sendMsg(errorState.message!!)
            return
        }

        val talentInput = matcher.group(1)
        if (!restartUtil.isTalentFormatValid(talentInput)) {
            context.sendMsg(RestartRespEnum.TALENT_FORMAT_ERROR.message)
            return
        }
        when (restartUtil.talentCheck(talentInput, userInfo!!)) {
            TALENT_SELECT_NOT_COMPLETE -> context.sendMsg("要选满 ${userInfo.talentSelectLimit} 个不同的天赋,请重新选择")

            TALENT_SELECT_Limit -> context.sendMsg("只能选择 ${userInfo.talentSelectLimit} 个天赋,请重新选择")

            else -> {
                restartUtil.getChoiceTalent(talentInput, userInfo)
                restartUtil.getTalentAllocationAddition(userInfo)

                // 清空临时天赋列表并更新缓存
                userInfo.randomTalentTemp = mutableListOf()
                restartUtil.updateUserInfo(userInfo)

                context.sendMsg("请输入「分配 颜值 智力 体质 家境」或者「随机」来获取随机属性,你总共有 ${userInfo.status} 点属性可以分配")
            }
        }
    }

    @SystemLog(businessName = "随机分配人生重开游戏属性")
    @AParameter
    @Executor(action = "随机")
    fun randomAttribute(context: BotUtils.Context, matcher: Matcher) {
        val realId = context.getEvent().getRealUserId()
        val userInfo = restartUtil.findUserInfo(realId)

        val errorState = restartUtil.errorState(userInfo, LifeRestartUtil.OperationType.UNALLOCATED)
        if (errorState.code != RestartRespEnum.SUCCESS.code) {
            context.sendMsg(errorState.message!!)
            return
        }

        lifeRestartService.insertTimesByRealId(realId)
        restartUtil.randomAttributes(userInfo!!)

        val ageData = JacksonUtil.readTree(redisService.getValue("lifeRestart:ageData").toString())
        val eventData = JacksonUtil.readTree(redisService.getValue("lifeRestart:eventData").toString())
        val sendMsg = restartUtil.trajectory(userInfo, ageData, eventData)

        val sendMsgList = listOf(sendMsg)

        // 将发送消息缓存
        redisService.setValueWithExpiry("lifeRestart:sendMessage:${realId}", sendMsgList, 5L, TimeUnit.MINUTES)

        // 胎死腹中 直接结束游戏
        if (userInfo.isEnd == true) {
            restartUtil.sendGameEnd(context, userInfo)
            redisService.setExpire("lifeRestart:userInfo:${realId}", Duration.of(5L, ChronoUnit.SECONDS))
            return
        }

        userInfo.propertyDistribution = true
        restartUtil.updateAndSend(context, userInfo, realId, RestartRespEnum.CONTINUER_SUCCESS.message)
    }

    @SystemLog(businessName = "手动分配人生重开游戏属性")
    @AParameter
    @Executor(action = "分配 (.*)")
    fun dealAttribute(context: BotUtils.Context, matcher: Matcher) {
        val realId = context.getEvent().getRealUserId()
        val userInfo = restartUtil.findUserInfo(realId)


        if (!Regex("^\\d+( \\d+)*\$").matches(matcher.group(1))) {
            context.sendMsg(RestartRespEnum.ASSIGN_ATTRIBUTES_ERROR.message)
            return
        }

        // 检查其他状态是否正确
        val errorState = restartUtil.errorState(userInfo, LifeRestartUtil.OperationType.UNALLOCATED)
        if (errorState.code != RestartRespEnum.SUCCESS.code) {
            context.sendMsg(errorState.message!!)
            return
        }

        // 检查分配属性是否正确
        val assignState = restartUtil.assignAttributes(userInfo!!, matcher)
        if (assignState.code != RestartRespEnum.SUCCESS.code) {
            context.sendMsg(assignState.message)
        }
        userInfo.propertyDistribution = true

        val ageData = JacksonUtil.readTree(redisService.getValue("lifeRestart:ageData").toString())
        val eventData = JacksonUtil.readTree(redisService.getValue("lifeRestart:eventData").toString())
        val sendMsg = restartUtil.trajectory(userInfo, ageData, eventData)

        lifeRestartService.insertTimesByRealId(realId)

        val sendMsgList = listOf(sendMsg)
        redisService.setValueWithExpiry("lifeRestart:sendMessage:${realId}", sendMsgList, 5L, TimeUnit.MINUTES)

        if (userInfo.isEnd == true) {
            restartUtil.sendGameEnd(context, userInfo)
            redisService.setExpire("lifeRestart:userInfo:${realId}", Duration.of(5L, ChronoUnit.SECONDS))
            redisService.deleteKey("lifeRestart:sendMessage:${realId}")
            System.gc()
            return
        }

        restartUtil.updateAndSend(context, userInfo, realId, RestartRespEnum.CONTINUER_SUCCESS.message)
    }

    @SystemLog(businessName = "继续下一步人生重开游戏")
    @AParameter
    @Executor(action = "继续(.*)")
    fun continueGame(context: BotUtils.Context, matcher: Matcher) {
        val realId = context.getEvent().getRealUserId()
        val userInfo = restartUtil.findUserInfo(realId)

        // 判断错误状态
        val errorState = restartUtil.errorState(userInfo, LifeRestartUtil.OperationType.CONTINUE)
        if (errorState.code != RestartRespEnum.SUCCESS.code) {
            context.sendMsg(errorState.message!!)
            return
        }

        val ageData = JacksonUtil.readTree(redisService.getValue("lifeRestart:ageData").toString())
        val eventData = JacksonUtil.readTree(redisService.getValue("lifeRestart:eventData").toString())
        val stepNext = matcher.group(1).trim().toIntOrNull() ?: 1 // 默认为1步

        val strList = mutableListOf<List<LifeRestartUtil.SendListEntity>>()
        for (i in 1..stepNext) {
            val sendMessage = restartUtil.trajectory(userInfo!!, ageData, eventData)
            strList.add(sendMessage)

            // 判断是否结束游戏
            if (userInfo.isEnd == true) {
                handleGameEnd(context, userInfo, realId, strList)
                return
            }
        }

        updateSendMessages(realId, strList)
        restartUtil.updateAndSend(context, userInfo!!, realId)
    }

    @Suppress("UNCHECKED_CAST")
    private fun handleGameEnd(
        context: BotUtils.Context,
        userInfo: bot.wuliang.entity.UserInfoEntity,
        realId: String,
        strList: List<List<LifeRestartUtil.SendListEntity>>
    ) {
        val currentMsgList = redisService.getValue("lifeRestart:sendMessage:$realId")
        val sendMsgList =
            (currentMsgList as? MutableList<List<LifeRestartUtil.SendListEntity>>)?.toMutableList() ?: mutableListOf()

        sendMsgList.addAll(strList)

        redisService.setValueWithExpiry("lifeRestart:sendMessage:$realId", sendMsgList, 5L, TimeUnit.MINUTES)
        restartUtil.sendGameEnd(context, userInfo)
        redisService.setExpire("lifeRestart:userInfo:$realId", Duration.of(5L, ChronoUnit.SECONDS))
        redisService.deleteKey("lifeRestart:sendMessage:$realId")
        System.gc()
    }

    @Suppress("UNCHECKED_CAST")
    private fun updateSendMessages(realId: String, strList: List<List<LifeRestartUtil.SendListEntity>>) {
        val currentMsgList = redisService.getValue("lifeRestart:sendMessage:$realId")
        val sendMsgList =
            (currentMsgList as? MutableList<List<LifeRestartUtil.SendListEntity>>)?.toMutableList() ?: mutableListOf()

        sendMsgList.addAll(strList)
        redisService.setValueWithExpiry("lifeRestart:sendMessage:$realId", sendMsgList, 5L, TimeUnit.MINUTES)
    }


}