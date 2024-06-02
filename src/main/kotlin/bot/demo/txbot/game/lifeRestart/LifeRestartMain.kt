package bot.demo.txbot.game.lifeRestart

import bot.demo.txbot.common.utils.OtherUtil
import bot.demo.txbot.common.utils.WebImgUtil
import bot.demo.txbot.game.lifeRestart.datebase.LifeRestartService
import com.mikuac.shiro.annotation.AnyMessageHandler
import com.mikuac.shiro.annotation.MessageHandlerFilter
import com.mikuac.shiro.annotation.common.Shiro
import com.mikuac.shiro.common.utils.MsgUtils
import com.mikuac.shiro.core.Bot
import com.mikuac.shiro.dto.event.message.AnyMessageEvent
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.regex.Matcher


/**
 * @description: 人生重开主文件
 * @author Nature Zero
 * @date 2024/2/14 18:54
 */
@Shiro
@Component
class LifeRestartMain {

    @Autowired
    lateinit var lifeRestartService: LifeRestartService

    companion object {
        val restartUtil = LifeRestartUtil()
        var userList = mutableListOf<LifeRestartUtil.UserInfo>()
        var lastFetchTime: Long = 0
        var sendStrList: MutableList<MutableMap<String, Any>> = mutableListOf()
    }


    fun sendNewImage(
        bot: Bot,
        event: AnyMessageEvent?,
        imgName: String,
        webUrl: String,
        scale: Double? = null,
        useUrlImg: Boolean? = false
    ) {
        val imgData = WebImgUtil.ImgData(url = webUrl, imgName = imgName, element = "body", scale = scale)
        val imgUrl = if (useUrlImg == true) WebImgUtil().returnUrlImg(imgData)
        else WebImgUtil().returnBs4Img(imgData)
        val sendMsg: String = MsgUtils.builder().img(imgUrl).build()
        bot.sendMsg(event, sendMsg, false)
    }

    fun updateGameTime(userInfo: LifeRestartUtil.UserInfo) {
        // 更新数据时间戳
        lastFetchTime = System.currentTimeMillis()
        // 更新用户活动时间
        userInfo.activeGameTime = System.currentTimeMillis()
    }


    @Scheduled(fixedDelay = 1 * 60 * 1000) // 每隔1分钟执行一次检查
    fun clearCacheIfExpired() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastFetchTime > 5 * 60 * 1000 && (restartUtil.eventData != null || restartUtil.ageData != null)) {
            restartUtil.eventData = null
            restartUtil.ageData = null
            userList.forEach { userInfo ->
                if (currentTime - userInfo.activeGameTime > 5 * 60 * 1000) {
                    userList.remove(userInfo)
                }
            }

            System.gc()
        }
    }

    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "重开")
    fun startRestart(bot: Bot, event: AnyMessageEvent, matcher: Matcher) {
        val currentTime = System.currentTimeMillis()
        // 如果超过5分钟或者没有获取过数据，重新获取
        if (currentTime - lastFetchTime > 5 * 60 * 1000 || restartUtil.eventData == null || restartUtil.ageData == null) {
            if (!restartUtil.fetchDataAndUpdateLists()) {
                bot.sendMsg(event, "人生重开数据缺失，请使用「更新资源」指令来下载缺失数据", false)
                return
            }
        }

        val realId = OtherUtil().getRealId(event)

        userList.find { it.userId == realId }.let {
            if (it != null) {
                userList.remove(it)
            }
        }

        sendStrList.find { sendMap ->
            sendMap["userId"] == realId
        }.let { sendMap ->
            sendStrList.remove(sendMap)
        }

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

        // 更新数据与账户时间戳
        updateGameTime(userInfo)

        userList.add(userInfo)

        val randomTalent = restartUtil.talentRandomInit(userInfo = userInfo)
        userInfo.randomTalentTemp = randomTalent

        bot.sendMsg(
            event,
            "游戏账号创建成功，请使用如「天赋 1 2 3」来选择天赋",
            false
        )

        bot.sendMsg(event, "请在5分钟内开始游戏", false)

        sendNewImage(
            bot,
            event,
            "${userInfo.userId}-LifeStartTalent",
            "http://localhost:${WebImgUtil.usePort}/lifeRestartTalent?userId=${userInfo.userId}"
        )

    }

    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "天赋 (.*)")
    fun getTalent(bot: Bot, event: AnyMessageEvent, matcher: Matcher) {
        val realId = OtherUtil().getRealId(event)
        userList.find { it.userId == realId }.let { userInfo ->
            if (userInfo == null) {
                bot.sendMsg(event, "你还没有开始游戏，请发送「重开」进行游戏", false)
                return
            }
            if (userInfo.talent.isNotEmpty()) {
                bot.sendMsg(event, "你已经选择过天赋了,请不要重复分配", false)
                return
            }

            val pattern = Regex("""^(?:[1-9]|10)(?:\s(?:[1-9]|10))*$""")
            val match = matcher.group(1)
            if (!pattern.matches(match)) {
                bot.sendMsg(event, "你分配的天赋格式错误或范围不正确，请重新分配", false)
                return
            }

            when (restartUtil.talentCheck(match, userInfo)) {
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
                    // 更新数据与账户时间戳
                    updateGameTime(userInfo)

                    restartUtil.getChoiceTalent(match, userInfo)
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

    fun errorSituation(bot: Bot, event: AnyMessageEvent, userInfo: LifeRestartUtil.UserInfo? = null): Boolean {
        if (userInfo == null) {
            bot.sendMsg(event, "你还没有开始游戏，请发送「重开」进行游戏", false)
            return false
        }
        if (userInfo.propertyDistribution == true) {
            bot.sendMsg(event, "你已经分配过属性了,请不要重复分配", false)
            return false
        }
        if (userInfo.talent.isEmpty()) {
            bot.sendMsg(event, "你还没有选择天赋,请先选择天赋", false)
            return false
        }
        return true
    }

    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "随机")
    @Suppress("UNCHECKED_CAST")
    fun randomAttribute(bot: Bot, event: AnyMessageEvent, matcher: Matcher) {
        val realId = OtherUtil().getRealId(event)
        userList.find { it.userId == realId }.let { userInfo ->

            if (!errorSituation(bot, event, userInfo)) return
            lifeRestartService.insertTimesByRealId(realId)
            userInfo!!.property = restartUtil.randomAttributes(userInfo)

            // 更新数据与账户时间戳
            updateGameTime(userInfo)

            val sendStr = restartUtil.trajectory(userInfo)
            sendStrList.add(mutableMapOf("userId" to realId, "sendStr" to mutableListOf(sendStr) as List<String>))

            sendNewImage(
                bot,
                event,
                "${userInfo.userId}-LifeStart",
                "http://localhost:${WebImgUtil.usePort}/lifeRestart?userId=${userInfo.userId}"
            )

            bot.sendMsg(event, "请发送「继续」来进行游戏", false)
            userInfo.propertyDistribution = true
        }
    }

    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "分配 (.*)")
    fun dealAttribute(bot: Bot, event: AnyMessageEvent, matcher: Matcher) {
        val realId = OtherUtil().getRealId(event)

        userList.find { it.userId == realId }.let { userInfo ->

            if (!errorSituation(bot, event, userInfo)) return
            val pattern = Regex("^\\d+( \\d+)*\$")
            if (!pattern.matches(matcher.group(1))) {
                bot.sendMsg(event, "你分配的属性格式错误，请重新分配", false)
                return
            }

            when (restartUtil.assignAttributes(userInfo!!, matcher)) {
                SIZE_OUT -> {
                    bot.sendMsg(event, "注意分配的5个属性值的和不能超过${userInfo.status}哦", false)
                    return
                }

                VALUE_OUT -> {
                    bot.sendMsg(event, "单项属性值不能大于10", false)
                    return
                }
            }
            lifeRestartService.insertTimesByRealId(realId)

            // 更新数据与账户时间戳
            updateGameTime(userInfo)

            val sendStr = restartUtil.trajectory(userInfo)
            sendStrList.add(mutableMapOf("userId" to realId, "sendStr" to mutableListOf(sendStr) as List<Any?>))
            sendNewImage(
                bot,
                event,
                "${userInfo.userId}-LifeStart",
                "http://localhost:${WebImgUtil.usePort}/lifeRestart?userId=${userInfo.userId}"
            )

            userInfo.propertyDistribution = true
        }
    }

    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "继续")
    fun nextStep(bot: Bot, event: AnyMessageEvent, matcher: Matcher) {
        val realId = OtherUtil().getRealId(event)
        userList.find { it.userId == realId }.let { userInfo ->
            if (userInfo == null) {
                bot.sendMsg(event, "你还没有开始游戏，请发送「重开」进行游戏", false)
                return
            }
            if (userInfo.propertyDistribution == false) {
                bot.sendMsg(event, "你还没有分配属性，请先分配属性", false)
                return
            }
            if (userInfo.talent.isEmpty()) {
                bot.sendMsg(event, "你还没有选择天赋,请先选择天赋", false)
                return
            }

            if (userInfo.isEnd == true) {
                sendNewImage(
                    bot,
                    event,
                    "${userInfo.userId}-LifeStart",
                    "http://localhost:${WebImgUtil.usePort}/lifeRestart?userId=${userInfo.userId}",
                    useUrlImg = true
                )
                bot.sendMsg(event, "游戏结束", false)
                userList.remove(userInfo)
                return
            }
            // 更新数据与账户时间戳
            updateGameTime(userInfo)

            val sendStr = restartUtil.trajectory(userInfo)
            sendStrList.find { sendMap ->
                sendMap["userId"] == realId
            }.let { sendMap ->
                sendMap?.set("sendStr", listOf(sendStr))
            }

            sendNewImage(
                bot,
                event,
                "${userInfo.userId}-LifeStart",
                "http://localhost:${WebImgUtil.usePort}/lifeRestart?userId=${userInfo.userId}"
            )
        }
    }


    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "继续 (.*)")
    fun nextTenStep(bot: Bot, event: AnyMessageEvent, matcher: Matcher) {
        val realId = OtherUtil().getRealId(event)
        val stepNext = matcher.group(1).toInt()
        userList.find { it.userId == realId }.let { userInfo ->
            if (userInfo == null) {
                bot.sendMsg(event, "你还没有开始游戏，请发送「重开」进行游戏", false)
                return
            }
            if (userInfo.property == null) {
                bot.sendMsg(event, "你还没有分配属性，请先分配属性", false)
                return
            }
            // 更新数据与账户时间戳
            updateGameTime(userInfo)

            val strList = mutableListOf<Any?>()
            for (i in 1..stepNext) {

                val sendStr = restartUtil.trajectory(userInfo)

                strList.add(sendStr)

                sendStrList.find { sendMap ->
                    sendMap["userId"] == realId
                }.let { sendMap ->
                    sendMap?.set("sendStr", strList)
                }

                if (userInfo.isEnd == true) {
                    sendNewImage(
                        bot,
                        event,
                        "${userInfo.userId}-LifeStart",
                        "http://localhost:${WebImgUtil.usePort}/lifeRestart?userId=${userInfo.userId}"
                    )
                    userList.remove(userInfo)
                    bot.sendMsg(event, "游戏结束", false)
                    return
                }
            }

            sendNewImage(
                bot,
                event,
                "${userInfo.userId}-LifeStart",
                "http://localhost:${WebImgUtil.usePort}/lifeRestart?userId=${userInfo.userId}"
            )
        }
    }
}