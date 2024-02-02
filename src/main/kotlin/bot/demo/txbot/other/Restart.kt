package bot.demo.txbot.other

import com.mikuac.shiro.annotation.AnyMessageHandler
import com.mikuac.shiro.annotation.MessageHandlerFilter
import com.mikuac.shiro.annotation.common.Shiro
import com.mikuac.shiro.core.Bot
import com.mikuac.shiro.dto.event.message.AnyMessageEvent
import kotlinx.coroutines.runBlocking
import org.springframework.stereotype.Component
import java.util.regex.Matcher

@Shiro
@Component
class Restart {

    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "重启")
    fun allPool(bot: Bot, event: AnyMessageEvent?, matcher: Matcher?) {
        bot.sendMsg(event, "正在重启中，请稍后", false)
        // 获取操作系统类型
        val osName = System.getProperty("os.name").toLowerCase()

        val jarDirectory = System.getProperty("user.dir")
        // 根据操作系统类型选择相应的脚本
        val scriptPath = if (osName.contains("win")) {
            // 如果是Windows系统
            System.getProperty("user.dir") + "\\restart.bat"
        } else {
            // 如果是Linux系统
            System.getProperty("user.dir") + "/restart.sh"
        }

        // 执行Shell脚本或批处理文件
        runBlocking {
            val process = Runtime.getRuntime().exec(scriptPath)
            process.waitFor()
        }
    }
}