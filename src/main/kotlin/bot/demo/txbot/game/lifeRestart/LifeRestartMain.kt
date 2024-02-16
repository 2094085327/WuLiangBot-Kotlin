package bot.demo.txbot.game.lifeRestart

import bot.demo.txbot.common.utils.ExcelReader
import bot.demo.txbot.common.utils.OtherUtil
import com.mikuac.shiro.annotation.AnyMessageHandler
import com.mikuac.shiro.annotation.MessageHandlerFilter
import com.mikuac.shiro.annotation.common.Shiro
import com.mikuac.shiro.core.Bot
import com.mikuac.shiro.dto.event.message.AnyMessageEvent
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

    var eventList: Any = EventDataVO()
    var ageList: Any = AgeDataVO()
    var userList = mutableListOf<LifeRestartUtil.UserInfo>()


    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "重开")
    fun startRestart(bot: Bot, event: AnyMessageEvent, matcher: Matcher) {
        val readEvent =
            ExcelReader().readExcel(
                "E:\\Learning\\bots\\Tencent-Bot-Kotlin\\resources\\lifeRestart\\events.xlsx",
                "event"
            )

        val readAge = ExcelReader().readExcel(
            "E:\\Learning\\bots\\Tencent-Bot-Kotlin\\resources\\lifeRestart\\age.xlsx",
            "age"
        )

        if (readEvent != null) {
            eventList = readEvent
        }
        if (readAge != null) {
            ageList = readAge
        }

        val realId = OtherUtil().getRealId(event)




        for (userInfo in userList) {
            if (userInfo.userId == realId) {
                userList.remove(userInfo)
                break
            }
        }
        println(userList)


        userList.add(LifeRestartUtil.UserInfo(realId, null, 0, null))
        bot.sendMsg(
            event,
            "游戏账号创建成功，请输入「分配属性 以空格隔开的5个总和不超过10的数字」或者「随机分配」来获取随机属性",
            false
        )

    }

    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "随机分配")
    fun randomAttribute(bot: Bot, event: AnyMessageEvent, matcher: Matcher) {
        val realId = OtherUtil().getRealId(event)
        var foundUser = false
        for (userInfo in userList) {
            if (userInfo.userId == realId) {
                if (userInfo.attributes != null) {
                    bot.sendMsg(event, "你已经分配过属性了,请不要重复分配", false)
                    return
                }
                userInfo.attributes = LifeRestartUtil().randomAttributes()
                bot.sendMsg(event, "你的初始属性为：${userInfo.attributes}", false)
                foundUser = true
                break
            }
        }
        if (!foundUser) {
            bot.sendMsg(event, "你还没有开始游戏，请发送 重开 进行游戏", false)
            return
        }
    }

    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "分配属性")
    fun dealAttribute(bot: Bot, event: AnyMessageEvent, matcher: Matcher) {

    }
}