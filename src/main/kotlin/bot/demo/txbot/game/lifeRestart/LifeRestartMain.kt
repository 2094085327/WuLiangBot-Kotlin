package bot.demo.txbot.game.lifeRestart

import bot.demo.txbot.common.utils.OtherUtil
import bot.demo.txbot.common.utils.WebImgUtil
import bot.demo.txbot.game.lifeRestart.datebase.LifeRestartService
import com.mikuac.shiro.annotation.AnyMessageHandler
import com.mikuac.shiro.annotation.MessageHandlerFilter
import com.mikuac.shiro.annotation.common.Shiro
import com.mikuac.shiro.core.Bot
import com.mikuac.shiro.dto.event.message.AnyMessageEvent
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.*
import java.util.regex.Matcher


/**
 * @description: 人生重开主文件
 * @author Nature Zero
 * @date 2024/2/14 18:54
 */
@Shiro
@Component
class LifeRestartMain(
    @Autowired private var lifeRestartService: LifeRestartService,
    @Autowired private val webImgUtil: WebImgUtil
) {
    companion object {
        val restartUtil = LifeRestartUtil()
        var userList = mutableListOf<LifeRestartUtil.UserInfo>()
        var lastFetchTime: Long = 0
        var sendStrList: MutableList<MutableMap<String, Any>> = mutableListOf()
    }

    /**
     * 更新游戏时间
     *
     * @param userInfo 用户信息
     */
    fun updateGameTime(userInfo: LifeRestartUtil.UserInfo) {
        val currentTime = System.currentTimeMillis()
        lastFetchTime = currentTime
        userInfo.activeGameTime = currentTime
    }

    /**
     * 天赋格式正则
     *
     * @param talent 天赋字符串
     * @return Boolean
     */
    fun isTalentFormatValid(talent: String): Boolean {
        return Regex("""^(?:[1-9]|10)(?:\s(?:[1-9]|10))*$""").matches(talent)
    }

    /**
     * 清理缓存
     *
     */
    @Scheduled(fixedDelay = 1 * 60 * 1000)
    fun clearCacheIfExpired() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastFetchTime > 5 * 60 * 1000) {
            restartUtil.eventData = null
            restartUtil.ageData = null
            userList.removeAll { currentTime - it.activeGameTime > 5 * 60 * 1000 }
            System.gc()
        }
    }


    /**
     * 错误状态判断
     *
     * @param bot 机器人
     * @param event 事件
     * @param userInfo 用户信息
     * @return
     */
    fun isErrorState(bot: Bot, event: AnyMessageEvent, userInfo: LifeRestartUtil.UserInfo?): Boolean {
        if (userInfo == null) {
            bot.sendMsg(event, "你还没有开始游戏，请发送「重开」进行游戏", false)
            return true
        }
        if (userInfo.talent.isEmpty()) {
            bot.sendMsg(event, "你还没有选择天赋,请先选择天赋", false)
            return true
        }
        if (!userInfo.propertyDistribution!!) {
            bot.sendMsg(event, "你还没有分配属性，请先分配属性", false)
            return true
        }
        return false
    }

    /**
     * 是否选择过天赋判断
     *
     * @param bot 机器人
     * @param event 事件
     * @param userInfo 用户信息
     * @return Boolean
     */
    fun isTalentError(bot: Bot, event: AnyMessageEvent, userInfo: LifeRestartUtil.UserInfo?): Boolean {
        if (userInfo == null) {
            bot.sendMsg(event, "你还没有开始游戏，请发送「重开」进行游戏", false)
            return true
        }
        if (userInfo.talent.isNotEmpty()) {
            bot.sendMsg(event, "你已经选择过天赋了,请不要重复分配", false)
            return true
        }
        return false
    }

    /**
     * 查找用户信息
     *
     * @param realId 真实id
     * @return 找到的用户信息
     */
    fun findUserInfo(realId: String): LifeRestartUtil.UserInfo? {
        return userList.find { it.userId == realId }
    }

    /**
     * 处理游戏开始事件
     *
     * @param bot 机器人
     * @param event 事件
     * @param userInfo 用户信息
     * @param realId 真实id
     */
    fun handleGameStart(bot: Bot, event: AnyMessageEvent, userInfo: LifeRestartUtil.UserInfo, realId: String) {
        updateGameTime(userInfo)
        userList.add(userInfo)

        val randomTalent = restartUtil.talentRandomInit(userInfo = userInfo)
        userInfo.randomTalentTemp = randomTalent

        bot.sendMsg(event, "游戏账号创建成功，请使用如「天赋 1 2 3」来选择图片中的天赋", false)

        val imageData = WebImgUtil.ImgData(
            imgName = "${userInfo.userId}-LifeStartTalent-${UUID.randomUUID()}",
            url = "http://localhost:${webImgUtil.usePort}/lifeRestartTalent?userId=${userInfo.userId}"
        )

        webImgUtil.sendNewImage(bot, event, imageData)
        bot.sendMsg(event, "请在5分钟内开始游戏", false)
        webImgUtil.deleteImgByQiNiu(imageData)
    }

    /**
     * 更新时间并发送图片
     *
     * @param bot 机器人
     * @param event 事件
     * @param userInfo 用户信息
     * @param message 待发送的消息
     */
    fun updateAndSend(bot: Bot, event: AnyMessageEvent, userInfo: LifeRestartUtil.UserInfo, message: String? = null) {
        updateGameTime(userInfo)

        val sendStr = restartUtil.trajectory(userInfo)
        sendStrList.add(mutableMapOf("userId" to userInfo.userId, "sendStr" to listOf(sendStr)))

        val imageData = WebImgUtil.ImgData(
            imgName = "${userInfo.userId}-LifeStart-${UUID.randomUUID()}",
            url = "http://localhost:${webImgUtil.usePort}/lifeRestart?userId=${userInfo.userId}"
        )

        webImgUtil.sendNewImage(bot, event, imageData)
        if (message != null) bot.sendMsg(event, message, false)
        webImgUtil.deleteImgByQiNiu(imageData)
    }

    /**
     * 发送游戏结束信息
     *
     * @param bot 机器人
     * @param event 事件
     * @param userInfo 用户信息
     */
    fun sendGameEnd(bot: Bot, event: AnyMessageEvent, userInfo: LifeRestartUtil.UserInfo) {
        val imageData = WebImgUtil.ImgData(
            imgName = "${userInfo.userId}-LifeStart-${UUID.randomUUID()}",
            url = "http://localhost:${webImgUtil.usePort}/lifeRestart?userId=${userInfo.userId}"
        )
        webImgUtil.sendNewImage(bot, event, imageData)
        bot.sendMsg(event, "游戏结束", false)
        userList.remove(userInfo)
        webImgUtil.deleteImgByQiNiu(imageData)
    }

    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "重开")
    fun startRestart(bot: Bot, event: AnyMessageEvent, matcher: Matcher) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastFetchTime > 5 * 60 * 1000 || restartUtil.eventData == null || restartUtil.ageData == null) {
            if (!restartUtil.fetchDataAndUpdateLists()) {
                bot.sendMsg(event, "人生重开数据缺失，请使用「更新资源」指令来下载缺失数据", false)
                return
            }
        }

        val realId = OtherUtil().getRealId(event)

        userList.removeIf { it.userId == realId }
        sendStrList.removeIf { it["userId"] == realId }

        val userGameInfo = lifeRestartService.selectRestartInfoByRealId(realId)
        val userInfo = LifeRestartUtil.UserInfo(
            userId = realId,
            attributes = null,
            age = -1,
            events = mutableListOf(),
            property = null,
            gameTimes = userGameInfo?.times ?: 0,
            achievement = userGameInfo?.cachv ?: 0,
        )

        handleGameStart(bot, event, userInfo, realId)
    }

    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "天赋 (.*)")
    fun getTalent(bot: Bot, event: AnyMessageEvent, matcher: Matcher) {
        val realId = OtherUtil().getRealId(event)
        val userInfo = findUserInfo(realId)

        if (isTalentError(bot, event, userInfo)) return

        val talentInput = matcher.group(1)
        if (!isTalentFormatValid(talentInput)) {
            bot.sendMsg(event, "你分配的天赋格式错误或范围不正确，请重新分配", false)
            return
        }
        userInfo?.let {
            when (restartUtil.talentCheck(talentInput, userInfo)) {
                TALENT_SELECT_NOT_COMPLETE -> bot.sendMsg(
                    event,
                    "要选满 ${userInfo.talentSelectLimit} 个不同的天赋,请重新选择",
                    false
                )

                TALENT_SELECT_Limit -> bot.sendMsg(
                    event,
                    "只能选择 ${userInfo.talentSelectLimit} 个天赋,请重新选择",
                    false
                )

                else -> {
                    updateGameTime(userInfo)
                    restartUtil.getChoiceTalent(talentInput, userInfo)
                    restartUtil.getTalentAllocationAddition(userInfo)

                    bot.sendMsg(
                        event,
                        "请输入「分配 颜值 智力 体质 家境」或者「随机」来获取随机属性,你总共有 ${userInfo.status} 点属性可以分配",
                        false
                    )
                }
            }
        }
    }

    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "随机")
    fun randomAttribute(bot: Bot, event: AnyMessageEvent, matcher: Matcher) {
        val realId = OtherUtil().getRealId(event)
        val userInfo = findUserInfo(realId)

        if (userInfo == null) {
            bot.sendMsg(event, "你还没有开始游戏，请发送「重开」进行游戏", false)
            return
        }

        userInfo.let {
            lifeRestartService.insertTimesByRealId(realId)
            it.property = restartUtil.randomAttributes(it)
            updateAndSend(bot, event, it, "请发送「继续 继续的步数」来进行游戏")
            it.propertyDistribution = true
        }
    }

    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "分配 (.*)")
    fun dealAttribute(bot: Bot, event: AnyMessageEvent, matcher: Matcher) {
        val realId = OtherUtil().getRealId(event)
        val userInfo = findUserInfo(realId)

        if (userInfo == null) {
            bot.sendMsg(event, "你还没有开始游戏，请发送「重开」进行游戏", false)
            return
        }
        if (userInfo.talent.isEmpty()) {
            bot.sendMsg(event, "你还没有选择天赋,请先选择天赋", false)
            return
        }
        if (userInfo.propertyDistribution == true) {
            bot.sendMsg(event, "你已经分配过属性了，请不要重复分配", false)
            return
        }

        userInfo.let {
            if (!Regex("^\\d+( \\d+)*\$").matches(matcher.group(1))) {
                bot.sendMsg(event, "你分配的属性格式错误，请重新分配", false)
                return
            }

            when (restartUtil.assignAttributes(it, matcher)) {
                SIZE_OUT -> {
                    bot.sendMsg(event, "注意分配的5个属性值的和不能超过${it.status}哦", false)
                    return
                }

                VALUE_OUT -> {
                    bot.sendMsg(event, "单项属性值不能大于10", false)
                    return
                }
            }

            lifeRestartService.insertTimesByRealId(realId)
            updateAndSend(bot, event, it, "请发送「继续 继续的步数」来进行游戏")

            it.propertyDistribution = true
        }
    }

    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "继续(.*)")
    fun continueGame(bot: Bot, event: AnyMessageEvent, matcher: Matcher) {
        val realId = OtherUtil().getRealId(event)
        val userInfo = findUserInfo(realId)

        if (isErrorState(bot, event, userInfo)) return

        userInfo?.let {
            val stepNext = matcher.group(1).trim().toIntOrNull() ?: 1 // 默认为1步

            val strList = mutableListOf<Any?>()
            for (i in 1..stepNext) {
                val sendStr = restartUtil.trajectory(it)
                strList.add(sendStr)

                sendStrList.find { sendMap -> sendMap["userId"] == realId }?.set("sendStr", strList)

                if (it.isEnd == true) {
                    sendGameEnd(bot, event, it)
                    return
                }
            }

            updateAndSend(bot, event, it)
        }
    }
}