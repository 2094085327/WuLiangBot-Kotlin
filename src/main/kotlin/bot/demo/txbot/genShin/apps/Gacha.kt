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
    @MessageHandlerFilter(cmd = "启用卡池 (.*)")
    fun setOpenPool(bot: Bot, event: AnyMessageEvent?, matcher: Matcher?) {
        val poolData = matcher?.group(1) ?: ""
        println(poolData)
        if (poolData == "") {
            bot.sendMsg(event, "请输入正确的卡池", false)
            return
        }


        val poolName: String
        val poolId: String
        var poolType = "role"

        if (poolData.contains("-") && poolData.contains("武器")) {
            // 处理新的格式
            val parts = poolData.split("-")
            poolName = parts[0].trim()
            poolId = parts[1].replace("武器", "").trim()
            poolType = "weapon"
        } else if (poolData.contains("-")) {
            // 处理旧的格式
            val parts = poolData.split("-")
            poolName = parts[0].trim()
            poolId = parts[1].trim()
        } else {
            // 处理其他情况，这里可能需要根据具体需求进行调整
            bot.sendMsg(event, "你输入的格式似乎不正确哦", false)
            bot.sendMsg(event, "请使用指令 全部卡池 查看可以启用的卡池", false)
            return
        }

        val poolFormat = "$poolName-$poolId"


        val poolFind = MysDataUtil().findPoolData(poolName, poolId)
        if (poolFind == null) {
            bot.sendMsg(event, "未找到卡池$poolData", false)
            bot.sendMsg(event, "请使用指令 全部卡池 查看可以启用的卡池", false)
            return
        }
        MysDataUtil().changePoolOpen(poolFind, poolFormat, poolType)
        println(poolFind)

        bot.sendMsg(event, "已启用卡池「$poolData」", false)
    }

    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "十连")
    fun gacha(bot: Bot, event: AnyMessageEvent?, matcher: Matcher?) {
        val detailPoolInfo = MysDataUtil.poolData
        bot.sendMsg(
            event,
            "现在启用的卡池是「${detailPoolInfo["poolName"].textValue()}」,如果和你设置的卡池不一样可能是有其他人正在使用哦，可以等一下再尝试~",
            false
        )
        MysDataUtil().runGacha()
    }


}