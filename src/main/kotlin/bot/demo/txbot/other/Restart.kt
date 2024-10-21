package bot.demo.txbot.other

import bot.demo.txbot.common.botUtil.BotUtils.Context
import bot.demo.txbot.common.exception.RespBean
import bot.demo.txbot.common.utils.LoggerUtils.logInfo
import bot.demo.txbot.other.distribute.annotation.AParameter
import bot.demo.txbot.other.distribute.annotation.ActionService
import bot.demo.txbot.other.distribute.annotation.Executor
import bot.demo.txbot.other.systemResources.SystemResourcesMain
import bot.demo.txbot.other.systemResources.getTotal
import bot.demo.txbot.other.systemResources.getUsage
import kotlinx.coroutines.runBlocking
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.ResponseBody
import java.util.*

@Controller
@Component
@ActionService
class Restart {
    fun restartFunction() {
        logInfo("正在启动重启程序....")
        // 获取操作系统类型
        val osName = System.getProperty("os.name").lowercase(Locale.getDefault())

        // 根据操作系统类型选择相应的脚本
        // 如果是Windows系统
        val scriptPath = if (osName.contains("win")) System.getProperty("user.dir") + "\\restart.bat"
        else System.getProperty("user.dir") + "/restart.sh" // 如果是Linux系统

        // 执行Shell脚本或批处理文件
        runBlocking {
            val process = Runtime.getRuntime().exec(scriptPath)
            process.waitFor()
        }
    }


    @AParameter
    @Executor(action = "重启")
    fun restart(context: Context) {
        context.sendMsg("正在重启中，请稍后")
        restartFunction()
    }

    @Scheduled(cron = "0 0 2 * * ?")
    fun restartEveryDay() {
        val (cpuData, ramData) = SystemResourcesMain().resourcesMain()

        // 当内存使用率大于80%时重启
        if (ramData.getUsage() > 70.0) {
            logInfo("内存使用率大于70%，正在重启...")
            restartFunction()
        }

        if (cpuData.getTotal() > 70.0) {
            logInfo("cpu使用率大于70%，正在重启...")
            restartFunction()
        }
    }

    @PostMapping("/restartManage")
    @ResponseBody
    fun restartManage(): RespBean {
        logInfo("手动重启程序")
        restartFunction()
        return RespBean.success()
    }

    @PostMapping("/ping")
    @ResponseBody
    fun ping(): RespBean {
        // 服务器状态是否正常
        return RespBean.success()
    }

}