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
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
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
        val restartFile = File(RESTART_CONFIG)
        val restartConfig = objectMapper.readTree(restartFile) as ObjectNode
        // 修改重启配置文件为 jarName
        restartConfig.put("jar_file", jarName)
        objectMapper.writeValue(restartFile, restartConfig)
        return RespBean.success()
    }

    @GetMapping("/getRestartConfig")
    @ResponseBody
    fun getRestartConfig(): RespBean {
        val restartFile = File(RESTART_CONFIG)
        return RespBean.success(objectMapper.readTree(restartFile))
    }

    fun restartFunction() {
        logInfo("正在启动重启程序....")
        // 获取操作系统类型
        val osName = System.getProperty("os.name").lowercase(Locale.getDefault())

        // 根据操作系统类型选择相应的脚本
        // 如果是Windows系统
        val scriptPath = if (osName.contains("win")) System.getProperty("user.dir") + "\\restart.bat"
        else System.getProperty("user.dir") + "/restart.sh" // 如果是Linux系统

        val restartConfig = getRestartConfig().obj as JsonNode
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

    @GetMapping("/restartManage")
    @ResponseBody
    fun restartManage(): RespBean {
        logInfo("手动重启程序")
        restartFunction()
        return RespBean.success()
    }

    @GetMapping("/ping")
    @ResponseBody
    fun ping(): RespBean {
        // 服务器状态是否正常
        return RespBean.success()
    }

    @PostMapping("/cancelUpload")
    @ResponseBody
    fun cancelUpload(@RequestBody params: Map<String, String>) {
        // 处理取消上传,清理临时文件
        val folder = params["folder"]
        val folderDir = File(FILE_CACHE_PATH, folder!!)
        folderDir.listFiles()?.forEach { chunkFile ->
            chunkFile.delete() // 删除切片文件
        }
        folderDir.delete()
    }

    @PostMapping("/mergeUpload")
    @ResponseBody
    fun mergeUpload(@RequestBody params: Map<String, String>): RespBean {
        val folder = params["folder"]
        val folderDir = File(FILE_CACHE_PATH, folder!!)

        // 获取完整文件的目标路径
        val mergedFile = File(".", "$folder.jar") // 合并后的文件名可以自定义

        try {
            FileOutputStream(mergedFile).use { fos ->
                // 列出目录下的所有切片文件
                val chunkFiles = folderDir.listFiles { _, name -> name.endsWith(".part") } // 切片文件以 .part 结尾

                // 检查切片文件是否存在
                if (chunkFiles == null || chunkFiles.isEmpty()) {
                    return RespBean.error(RespBeanEnum.FILE_MERGE_FAIL) // 返回未找到切片的错误
                }

                // 排序以确保按顺序合并
                Arrays.sort(chunkFiles, Comparator.comparingInt { file ->
                    val fileName = file.name
                    fileName.substring(fileName.lastIndexOf('_') + 1, fileName.lastIndexOf('.')).toInt()
                })

                // 依次读取每个切片并写入合并文件
                for (chunkFile in chunkFiles) {
                    FileInputStream(chunkFile).use { fis ->
                        fis.copyTo(fos) // 使用 copyTo 方法将内容写入合并文件
                    }
                }
            }
        } catch (e: IOException) {
            return RespBean.error(RespBeanEnum.FILE_MERGE_FAIL)
        }

        // 合并完成后，删除切片文件
        folderDir.listFiles()?.forEach { chunkFile ->
            chunkFile.delete() // 删除切片文件
        }
        folderDir.delete()

        return RespBean.success()
    }

    @PostMapping("/uploadChunk")
    @ResponseBody
    fun uploadChunk(
        @RequestParam file: MultipartFile,
        @RequestParam fileName: String?,
        @RequestParam folder: String,
        @RequestParam(required = false) encrypt: String?
    ): RespBean {
        try {
            // 创建切片存储目录
            val folderDir = File(FILE_CACHE_PATH, folder)
            if (!folderDir.exists()) {
                folderDir.mkdirs()
            }

            // 保存切片文件
            val chunkFile = File(folderDir, file.originalFilename!!)
            FileOutputStream(chunkFile, true).use { fos ->
                fos.write(file.bytes)
            }
            return RespBean.success()
        } catch (e: IOException) {
            return RespBean.error(RespBeanEnum.JAR_UPLOAD_FAIL)
        }
    }

}