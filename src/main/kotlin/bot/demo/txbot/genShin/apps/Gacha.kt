package bot.demo.txbot.genShin.apps

import bot.demo.txbot.genShin.util.MysDataUtil
import com.mikuac.shiro.annotation.AnyMessageHandler
import com.mikuac.shiro.annotation.MessageHandlerFilter
import com.mikuac.shiro.annotation.common.Shiro
import com.mikuac.shiro.core.Bot
import com.mikuac.shiro.dto.event.message.AnyMessageEvent
import org.springframework.stereotype.Component
import java.util.regex.Matcher

@Shiro
@Component
class Gacha {
    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "全部卡池")
    fun allPool(bot: Bot, event: AnyMessageEvent?, matcher: Matcher?) {
        val poolList = MysDataUtil().findEachPoolName()
        bot.sendMsg(event, poolList.joinToString("\n"), false)
    }

    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "启用卡池(.*)")
    fun setOpenPool(bot: Bot, event: AnyMessageEvent?, matcher: Matcher?) {
        val poolData = matcher?.group(1) ?: ""
        if (poolData == "") {
            bot.sendMsg(event, "请输入正确的卡池", false)
            return
        }
        val poolName = poolData.split("-")[0]
        val poolId = poolData.split("-")[1]
        val poolFormat = "$poolName-$poolId"


        val poolFind = MysDataUtil().findPoolData(poolName, poolId)
        if (poolFind == null) {
            bot.sendMsg(event, "未找到卡池$poolData", false)
            bot.sendMsg(event, "请使用指令 全部卡池 查看可以启用的卡池", false)
            return
        }
        MysDataUtil().changePoolOpen(poolFind, poolFormat)
        println(poolFind)

        bot.sendMsg(event, "已启用卡池$poolData", false)
    }

    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "十连")
    fun gacha(bot: Bot, event: AnyMessageEvent?, matcher: Matcher?) {
        MysDataUtil().getGachaPool()
    }


}