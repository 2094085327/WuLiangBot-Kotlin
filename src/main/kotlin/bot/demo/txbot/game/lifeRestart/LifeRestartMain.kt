package bot.demo.txbot.game.lifeRestart

import bot.demo.txbot.common.utils.ExcelReader
import bot.demo.txbot.common.utils.OtherUtil
import com.mikuac.shiro.annotation.AnyMessageHandler
import com.mikuac.shiro.annotation.MessageHandlerFilter
import com.mikuac.shiro.annotation.common.Shiro
import com.mikuac.shiro.core.Bot
import com.mikuac.shiro.dto.event.message.AnyMessageEvent
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

    val restartUtil = LifeRestartUtil()
    var userList = mutableListOf<LifeRestartUtil.UserInfo>()
    var lastFetchTime: Long = 0

    @Scheduled(fixedDelay = 1 * 60 * 1000) // 每隔1分钟执行一次检查
    fun clearCacheIfExpired() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastFetchTime > 5 * 60 * 1000 && (restartUtil.eventList.isNotEmpty() || restartUtil.ageList.isNotEmpty())) {
            restartUtil.eventList.clear()
            restartUtil.ageList.clear()
            System.gc()
        }
    }

    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "重开")
    fun startRestart(bot: Bot, event: AnyMessageEvent, matcher: Matcher) {
        val currentTime = System.currentTimeMillis()
        // 如果超过5分钟或者没有获取过数据，重新获取
        if (currentTime - lastFetchTime > 5 * 60 * 1000 || restartUtil.eventList.isEmpty() || restartUtil.ageList.isEmpty()) {
            val readEvent = ExcelReader().readExcel("resources/lifeRestart/events.xlsx", "event")

            val readAge = ExcelReader().readExcel("resources/lifeRestart/age.xlsx", "age")

            if (readEvent != null) {
                restartUtil.eventList = readEvent
            }
            if (readAge != null) {
                restartUtil.ageList = readAge
            }

            // 更新时间戳
            lastFetchTime = currentTime
        }

        val realId = OtherUtil().getRealId(event)

        userList.find { it.userId == realId }.let {
            if (it != null) {
                userList.remove(it)
            }
        }
        userList.add(LifeRestartUtil.UserInfo(realId, null, 0, mutableListOf(),null))
        bot.sendMsg(
            event,
            "游戏账号创建成功，请输入「分配属性 颜值 智力 体质 家境 快乐」或者「随机分配」来获取随机属性",
            false
        )
        bot.sendMsg(event, "请在5分钟内开始游戏", false)

    }

    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "随机分配")
    fun randomAttribute(bot: Bot, event: AnyMessageEvent, matcher: Matcher) {
        val realId = OtherUtil().getRealId(event)
        userList.find { it.userId == realId }.let {
            if (it == null) {
                bot.sendMsg(event, "你还没有开始游戏，请发送 重开 进行游戏", false)
                return
            }
            if (it.attributes != null) {
                bot.sendMsg(event, "你已经分配过属性了,请不要重复分配", false)
                return
            }
            restartUtil.randomAttributes(it)
            restartUtil.eventInitial(userInfo = it, 0)
            bot.sendMsg(event, "你的初始属性为：${it.property}", false)
            bot.sendMsg(event, "请发送「继续」来进行游戏", false)
            println(it.events)
            val sendStr = it.events.find { events ->
                events as EventDataVO
                it.age == 0
            }.let { events ->
                events as EventDataVO
                events.event
            }
            bot.sendMsg(event, "0岁: $sendStr", false)
        }
    }

    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "分配属性 (.*)")
    fun dealAttribute(bot: Bot, event: AnyMessageEvent, matcher: Matcher) {
        val realId = OtherUtil().getRealId(event)

        userList.find { it.userId == realId }.let {
            if (it == null) {
                bot.sendMsg(event, "你还没有开始游戏，请发送 重开 进行游戏", false)
                return
            }
            if (it.attributes != null) {
                bot.sendMsg(event, "你已经分配过属性了,请不要重复分配", false)
                return
            }
            when (val dealResult = restartUtil.assignAttributes(it, matcher)) {
                "sizeOut" -> {
                    bot.sendMsg(event, "注意分配的5个属性值的和不能超过10哦", false)
                    return
                }

                else -> it.attributes = dealResult as Map<*, *>
            }
            bot.sendMsg(event, "你的初始属性为：${it.attributes}", false)
        }
    }

    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "继续")
    fun nextStep(bot: Bot, event: AnyMessageEvent, matcher: Matcher) {
        val realId = OtherUtil().getRealId(event)
        userList.find { it.userId == realId }.let {
            if (it == null) {
                bot.sendMsg(event, "你还没有开始游戏，请发送 重开 进行游戏", false)
                return
            }
            if (it.property == null) {
                bot.sendMsg(event, "你还没有分配属性，请先分配属性", false)
                return
            }
            restartUtil.eventInitial(userInfo = it, 0)

        }
    }
}