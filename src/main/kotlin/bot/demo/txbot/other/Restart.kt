package bot.demo.txbot.other

import bot.demo.txbot.common.botUtil.BotUtils.Context
import bot.demo.txbot.common.exception.RespBean
import bot.demo.txbot.common.exception.RespBeanEnum
import bot.demo.txbot.common.utils.LoggerUtils.logInfo
import bot.demo.txbot.other.distribute.annotation.AParameter
import bot.demo.txbot.other.distribute.annotation.ActionService
import bot.demo.txbot.other.distribute.annotation.Executor
import bot.demo.txbot.other.systemResources.SystemResourcesMain
import bot.demo.txbot.other.systemResources.getTotal
import bot.demo.txbot.other.systemResources.getUsage
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import kotlinx.coroutines.runBlocking
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody
import java.io.File
import java.util.*

@Controller
@Component
@ActionService
class Restart {
    private val objectMapper = ObjectMapper()

    @GetMapping("/getNowJar")
    @ResponseBody
    fun getNowJar(): RespBean {
        return try {
            val jarFileName = getNowJarName()
            if (!jarFileName.endsWith(".jar")) {
                RespBean.error(RespBeanEnum.JAR_NOT_RUN, jarFileName)
            } else RespBean.success(jarFileName)
        } catch (e: Exception) {
            RespBean.error(RespBeanEnum.JAR_ERROR, e)
        }
    }

    fun getNowJarName(): String {
        val classPath = System.getProperty("java.class.path")
        val paths = classPath.split(File.pathSeparator.toRegex()).dropLastWhile { it.isEmpty() }
            .toTypedArray()

        for (path in paths) {
            if (path.endsWith(".jar")) {
                return path
            }
        }
        return "非Jar下运行"
    }

    data class FileInfo(val name: String, val size: String, val lastModified: Date)

    @GetMapping("/allJarFile")
    @ResponseBody
    fun getAllJar(): RespBean {
        // 获取当前路径下所有.jar为结尾的文件名
        // 获取当前路径
        val currentDir = File(System.getProperty("user.dir"))

        // 获取当前路径下所有以 .jar 结尾的文件
        val jarFiles = currentDir.listFiles { file -> file.isFile && file.name.endsWith(".jar") }

        // 检查是否有 .jar 文件
        if (jarFiles == null || jarFiles.isEmpty()) {
            return RespBean.error(RespBeanEnum.JAR_NOT_FOUND)
        }

        val fileInfoList = jarFiles.map {
            FileInfo(
                name = it.name,
                size = String.format("%.2f", it.length() / (1024.0 * 1024.0)),
                lastModified = Date(it.lastModified())
            )
        }

        return RespBean.success(fileInfoList)
    }

    @PostMapping("/choseJar")
    @ResponseBody
    fun choseJar(@RequestParam("jar_name") jarName: String): RespBean {
        println(jarName)
        val restartFile = File(RESTART_CONFIG)
        val restartConfig = objectMapper.readTree(restartFile) as ObjectNode
        // 修改重启配置文件为 jarName
        restartConfig.put("jar_file", jarName)
        objectMapper.writeValue(restartFile, restartConfig)
        return RespBean.success()
    }

    @GetMapping("/getRestartConfig")
    @ResponseBody
    fun getRestartConfig(): JsonNode {
        val restartFile = File(RESTART_CONFIG)
        return objectMapper.readTree(restartFile)
    }

    fun restartFunction() {
        logInfo("正在启动重启程序....")
        // 获取操作系统类型
        val osName = System.getProperty("os.name").lowercase(Locale.getDefault())

        // 根据操作系统类型选择相应的脚本
        // 如果是Windows系统
        val scriptPath = if (osName.contains("win")) System.getProperty("user.dir") + "\\restart.bat"
        else System.getProperty("user.dir") + "/restart.sh" // 如果是Linux系统

        val restartConfig = getRestartConfig()

        val command = arrayOf(scriptPath, restartConfig["jar_file"].textValue(), "app.log")
        // 执行Shell脚本或批处理文件
        runBlocking {
            val process = Runtime.getRuntime().exec(command)
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