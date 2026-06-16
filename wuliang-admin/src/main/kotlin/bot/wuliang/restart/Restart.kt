package bot.wuliang.restart

import bot.wuliang.adapter.context.ExecutionContext
import bot.wuliang.botLog.logUtil.LoggerUtils.logError
import bot.wuliang.botLog.logUtil.LoggerUtils.logInfo
import bot.wuliang.config.CommonConfig.FILE_CACHE_PATH
import bot.wuliang.config.CommonConfig.RESTART_CONFIG
import bot.wuliang.controller.SystemResourcesMain
import bot.wuliang.distribute.annotation.AParameter
import bot.wuliang.distribute.annotation.ActionService
import bot.wuliang.distribute.annotation.Executor
import bot.wuliang.entity.getTotal
import bot.wuliang.entity.getUsage
import bot.wuliang.exception.RespBean
import bot.wuliang.exception.RespBeanEnum
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.io.*
import java.lang.management.ManagementFactory
import java.util.*
import kotlin.system.exitProcess


@Controller
@Component
@ActionService
class Restart {
    private val objectMapper = ObjectMapper()

    @GetMapping("/getNowJar")
    @ResponseBody
    fun getNowJar(): RespBean<out String> {
        return try {
            val jarFileName = getNowJarName()
            if (!jarFileName.endsWith(".jar")) {
                RespBean.error(RespBeanEnum.JAR_NOT_RUN, jarFileName)
            } else RespBean.success(jarFileName)
        } catch (_: Exception) {
            RespBean.error(RespBeanEnum.JAR_ERROR)
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
    fun getAllJar(): RespBean<out List<FileInfo>> {
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
    fun choseJar(@RequestParam("jar_name") jarName: String): RespBean<Nothing> {
        val restartFile = File(RESTART_CONFIG)
        val restartConfig = objectMapper.readTree(restartFile) as ObjectNode
        // 修改重启配置文件为 jarName
        restartConfig.put("jar_file", jarName)
        objectMapper.writeValue(restartFile, restartConfig)
        return RespBean.success()
    }

    @GetMapping("/getRestartConfig")
    @ResponseBody
    fun getRestartConfig(): RespBean<JsonNode> {
        val restartFile = File(RESTART_CONFIG)
        return RespBean.success(objectMapper.readTree(restartFile))
    }

    fun restartFunction() {
        logInfo("正在启动重启程序....")

        try {
            val jarPath = getNowJarName()
            if (!jarPath.endsWith(".jar")) {
                logError("当前不是JAR运行模式，无法自动重启")
                return
            }

            val javaHome = System.getProperty("java.home")
            val javaBin = File(javaHome, "bin/java").absolutePath

            val jvmArgs = ManagementFactory.getRuntimeMXBean().inputArguments

            val command = mutableListOf<String>()
            command.add(javaBin)
            command.addAll(jvmArgs)
            command.add("-jar")
            command.add(jarPath)

            val processBuilder = ProcessBuilder(command)
            processBuilder.directory(File(System.getProperty("user.dir")))

            val newProcess = processBuilder.start()
            logInfo("新进程已启动，PID: ${newProcess.pid()}")

            Runtime.getRuntime().addShutdownHook(Thread {
                logInfo("应用正在关闭...")
            })

            Thread.sleep(2000)
            exitProcess(0)

        } catch (e: Exception) {
            logError("重启失败: ${e.message}")
            e.printStackTrace()
        }
    }


    @AParameter
    @Executor(action = "重启")
    suspend fun restart(context: ExecutionContext) {
        context.sender.sendText("正在重启中，请稍后")
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
    fun restartManage(): RespBean<Nothing> {
        logInfo("手动重启程序")
        restartFunction()
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
    fun mergeUpload(@RequestBody params: Map<String, String>): RespBean<Nothing> {
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
        } catch (_: IOException) {
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
    ): RespBean<Nothing> {
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
        } catch (_: IOException) {
            return RespBean.error(RespBeanEnum.JAR_UPLOAD_FAIL)
        }
    }

    fun isJarRunning(jarPath: String): Boolean {
        val os = System.getProperty("os.name").lowercase()
        val command: List<String>

        return try {
            command = if (os.contains("win")) {
                // Windows 使用 WMIC 命令
                listOf("cmd", "/c", "wmic", "process", "where", "commandline like '%${jarPath}%'", "get", "processid")
            } else {
                // Linux/Mac 使用 ps 和 grep
                listOf("sh", "-c", "ps aux | grep java | grep -F '$jarPath' | grep -v grep")
            }

            val process = ProcessBuilder(command).start()
            val output = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor()

            // 判断输出是否有效
            if (os.contains("win")) {
                output.lines().any { it.trim().matches(Regex("\\d+")) }
            } else {
                output.isNotBlank()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    @PostMapping("/deleteJar")
    @ResponseBody
    fun deleteJar(@RequestParam("jar_name") jarName: String): RespBean<Nothing> {
        try {
            val jarFile = File(jarName)
            if (!jarFile.exists()) {
                return RespBean.error(RespBeanEnum.JAR_NOT_FOUND)
            }

            if (isJarRunning(jarName)) {
                return RespBean.error(RespBeanEnum.JAR_IN_USE)
            }

            jarFile.delete()
            return RespBean.success()
        } catch (_: Exception) {
            return RespBean.error(RespBeanEnum.JAR_ERROR)
        }
    }
}