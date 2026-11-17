package bot.wuliang.restart

import bot.wuliang.adapter.context.ExecutionContext
import bot.wuliang.botLog.logUtil.LoggerUtils.logError
import bot.wuliang.botLog.logUtil.LoggerUtils.logInfo
import bot.wuliang.controller.SystemResourcesMain
import bot.wuliang.distribute.annotation.AParameter
import bot.wuliang.distribute.annotation.ActionService
import bot.wuliang.distribute.annotation.Executor
import bot.wuliang.entity.getTotal
import bot.wuliang.entity.getUsage
import bot.wuliang.exception.RespBean
import bot.wuliang.exception.RespBeanEnum
import com.fasterxml.jackson.databind.JsonNode
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.io.File
import java.util.Date


@Controller
@Component
@ActionService
class Restart(private val restartManager: RestartManager, private val jarUploads: JarUploadManager) {

    @GetMapping("/getNowJar")
    @ResponseBody
    fun getNowJar(): RespBean<out String> {
        return try {
            val jarFileName = getNowJarName()
            if (!jarFileName.endsWith(".jar", ignoreCase = true)) {
                RespBean.error(RespBeanEnum.JAR_NOT_RUN, jarFileName)
            } else RespBean.success(jarFileName)
        } catch (_: Exception) {
            RespBean.error(RespBeanEnum.JAR_ERROR)
        }
    }

    fun getNowJarName(): String {
        return restartManager.currentJarName()
    }

    data class FileInfo(val name: String, val size: String, val lastModified: Date)

    @GetMapping("/allJarFile")
    @ResponseBody
    fun getAllJar(): RespBean<out List<FileInfo>> {
        // 获取当前路径下所有.jar为结尾的文件名
        // 获取当前路径
        val currentDir = File(System.getProperty("user.dir"))

        // 获取当前路径下所有以 .jar 结尾的文件
        val jarFiles = currentDir.listFiles { file -> file.isFile && file.name.endsWith(".jar", ignoreCase = true) }

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
        return try {
            restartManager.selectJar(jarName)
            RespBean.success()
        } catch (e: Exception) {
            RespBean.error(e.message ?: "选择 Jar 失败")
        }
    }

    @GetMapping("/getRestartConfig")
    @ResponseBody
    fun getRestartConfig(): RespBean<JsonNode> {
        return try {
            RespBean.success(restartManager.configuration())
        } catch (e: Exception) {
            RespBean.error(e.message ?: "读取重启配置失败")
        }
    }

    @GetMapping("/restartStatus")
    @ResponseBody
    fun restartStatus(): RespBean<RestartManager.Status> = RespBean.success(restartManager.status())

    fun restartFunction(): RespBean<RestartManager.Status> {
        return try {
            RespBean.success(restartManager.requestRestart())
        } catch (e: Exception) {
            logError("重启失败：{}", e.message, e)
            RespBean.error(e.message ?: "提交重启失败")
        }
    }


    @AParameter
    @Executor(action = "重启")
    suspend fun restart(context: ExecutionContext) {
        val result = restartFunction()
        context.sender.sendText(if (result.code == 200L) "正在重启中，请稍后" else "重启失败：${result.message}")
    }

    @Scheduled(cron = "0 0 2 * * ?")
    fun restartEveryDay() {
        val (cpuData, ramData) = SystemResourcesMain().resourcesMain()

        if (ramData.getUsage() > 70.0 || cpuData.getTotal() > 70.0) {
            logInfo("系统内存或 CPU 使用率大于70%，正在重启...")
            restartFunction()
        }
    }

    @GetMapping("/restartManage")
    @ResponseBody
    fun restartManage(): RespBean<RestartManager.Status> {
        logInfo("手动重启程序")
        return restartFunction()
    }

    data class InitializeUpload(val fileName: String, val size: Long)
    data class MergeUpload(val uploadId: String, val md5: String)
    data class CancelUpload(val uploadId: String)

    @PostMapping("/initJarUpload")
    @ResponseBody
    fun initializeUpload(@RequestBody request: InitializeUpload): RespBean<JarUploadManager.Initialized> =
        uploadResponse { jarUploads.initialize(request.fileName, request.size) }

    @PostMapping("/uploadChunk")
    @ResponseBody
    fun uploadChunk(@RequestParam file: MultipartFile, @RequestParam uploadId: String, @RequestParam index: Int): RespBean<Nothing> =
        uploadResponse {
            jarUploads.uploadChunk(uploadId, index, file)
            null
        }

    @PostMapping("/mergeUpload")
    @ResponseBody
    fun mergeUpload(@RequestBody request: MergeUpload): RespBean<JarUploadManager.Uploaded> =
        uploadResponse { jarUploads.merge(request.uploadId, request.md5) }

    @PostMapping("/cancelUpload")
    @ResponseBody
    fun cancelUpload(@RequestBody request: CancelUpload): RespBean<JarUploadManager.Uploaded> =
        uploadResponse { jarUploads.cancel(request.uploadId) }

    private fun <T> uploadResponse(action: () -> T?): RespBean<T> = try {
        RespBean.success(action())
    } catch (e: Exception) {
        logError("Jar 上传失败：{}", e.message)
        RespBean.error(e.message ?: "Jar 上传失败，请重试")
    }

    @PostMapping("/deleteJar")
    @ResponseBody
    fun deleteJar(@RequestParam("jar_name") jarName: String): RespBean<Nothing> {
        return try {
            restartManager.deleteJar(jarName)
            RespBean.success()
        } catch (e: Exception) {
            RespBean.error(e.message ?: "删除 Jar 失败")
        }
    }
}