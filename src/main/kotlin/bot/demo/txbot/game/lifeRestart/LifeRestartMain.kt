package bot.demo.txbot.game.lifeRestart

import bot.demo.txbot.common.utils.ExcelReader
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

    var eventList: Any = mutableListOf<EventDataVO>()


    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "重开")
    fun startRestart(bot: Bot, event: AnyMessageEvent, matcher: Matcher) {
        val readEvent =
            ExcelReader().readExcel(
                "E:\\Learning\\bots\\Tencent-Bot-Kotlin\\resources\\lifeRestart\\events.xlsx",
                "event"
            )

        if (readEvent != null) {
            eventList = readEvent
        }
    }
}