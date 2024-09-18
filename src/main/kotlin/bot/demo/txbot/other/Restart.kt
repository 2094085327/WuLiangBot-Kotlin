package bot.demo.txbot.other

import bot.demo.txbot.common.botUtil.BotUtils.Context
import bot.demo.txbot.other.distribute.annotation.AParameter
import bot.demo.txbot.other.distribute.annotation.ActionService
import bot.demo.txbot.other.distribute.annotation.Executor
import kotlinx.coroutines.runBlocking
import org.springframework.stereotype.Component
import java.util.*

@Component
@ActionService
class Restart {
    @AParameter
    @Executor(action = "重启")
    fun restart(context: Context) {
        context.sendMsg("正在重启中，请稍后")
        // 获取操作系统类型
        val osName = System.getProperty("os.name").lowercase(Locale.getDefault())

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