package bot.demo.txbot.other

import com.mikuac.shiro.annotation.AnyMessageHandler
import com.mikuac.shiro.annotation.MessageHandlerFilter
import com.mikuac.shiro.annotation.common.Shiro
import com.mikuac.shiro.core.Bot
import com.mikuac.shiro.dto.event.message.AnyMessageEvent
import org.springframework.stereotype.Component
import java.util.regex.Matcher

@Shiro
@Component
class Help {
    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "/help")
    fun help(bot: Bot, event: AnyMessageEvent?, matcher: Matcher?) {
        bot.sendMsg(
            event,
            "帮助菜单\n1.天气查询 [*]天气\n2.地理查询 [*]地理\n3.全部卡池\n4.启用卡池 [*]\n5.十连\n6.新增角色[*]\n7.清除缓存\n8.记录查询 [*]\n9.抽卡记录\n10.抽卡链接\n11.更新资源\n12.重开",
            false
        )
    }
}