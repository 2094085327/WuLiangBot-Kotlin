package bot.wuliang.updateResources

import bot.wuliang.botLog.logUtil.LoggerUtils.logError
import bot.wuliang.botLog.logUtil.LoggerUtils.logInfo
import bot.wuliang.botLog.logUtil.LoggerUtils.logWarn
import bot.wuliang.botUtil.BotUtils
import bot.wuliang.config.HELP_JSON
import bot.wuliang.config.RESOURCES_PATH
import bot.wuliang.distribute.annotation.AParameter
import bot.wuliang.distribute.annotation.ActionService
import bot.wuliang.distribute.annotation.Executor
import bot.wuliang.event.ResourceUpdateEvent
import bot.wuliang.otherUtil.OtherUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationContext
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.io.File
import javax.annotation.PostConstruct


/**
 * @description: 更新所用到的资源文件，包括图片、数据等
 * @author Nature Zero
 * @date 2024/3/12 8:08
 */
@Component
@ActionService
@RestController
class UpdateResources(private val applicationContext: ApplicationContext) {
    companion object {
        private var owner: String = ""
        private var repoName: String = ""
        private var accessToken: String? = null
    }

    private val scope = CoroutineScope(Dispatchers.Default)

    @Value("\${github.owner}")
    fun getOwner(githubOwner: String) {
        owner = githubOwner
    }

    @Value("\${github.repo}")
    fun getRepo(githubRepo: String) {
        repoName = githubRepo
    }

    @Value("\${github.access-token}")
    fun getAccessToken(githubAccessToken: String) {
        accessToken = githubAccessToken
    }

    val otherUtil = OtherUtil()

    fun manageFolderSize(folderPath: String, maxSizeMB: Double, deletePercentage: Double) {
        val folder = File(folderPath)
        if (!folder.exists() || !folder.isDirectory) {
            logWarn("指定路径不是文件夹或文件夹不存在")
            return
        }

        // 获取文件夹下所有文件
        val files = folder.listFiles()
        if (files.isNullOrEmpty()) {
            logWarn("缓存文件夹为空")
            return
        }

        // 计算当前文件夹的总大小，转换为MB
        val currentSizeMB = files.sumOf { it.length() } / (1024.0 * 1024.0)

        // 判断当前文件夹大小是否超过最大允许大小
        if (currentSizeMB <= maxSizeMB) {
            return
        }

        // 计算要删除的文件数量
        val filesToDeleteCount = (deletePercentage * files.size).toInt()

        // 按最久未修改时间排序文件
        val filesSortedByLastModified = files.sortedBy { it.lastModified() }

        // 删除最久未修改的部分文件
        var deletedSizeMB = 0.0
        for (i in 0 until filesToDeleteCount) {
            val fileToDelete = filesSortedByLastModified[i]
            val fileSizeMB = fileToDelete.length() / (1024.0 * 1024.0)
            val deleted = fileToDelete.delete()
            if (deleted) deletedSizeMB += fileSizeMB

            // 判断是否已删除足够大小的文件
            if (deletedSizeMB >= currentSizeMB - maxSizeMB) {
                break
            }
        }
    }

    /**
     * 强制删除缓存
     *
     * @param cachePath 缓存路径
     */
    fun forceDeleteCache(cachePath: String) {
        val folder = File(cachePath)
        folder.listFiles()?.forEach { file ->
            logInfo("删除缓存：${file.name}")
            file.delete()
        }
    }

    @AParameter
    @Executor(action = "更新资源")
    fun updateAll(context: BotUtils.Context) {
        context.sendMsg("正在更新资源，请稍后")

        val folderPath = RESOURCES_PATH

        runBlocking {
            val downloadCheck = otherUtil.downloadFolderFromGitHub(
                owner,
                repoName,
                folderPath,
                folderPath,
                accessToken
            )

            if (!downloadCheck.first) {
                context.sendMsg("资源更新失败,自动跳过此资源更新,请通知管理员检查错误或稍后再试")
                return@runBlocking
            }
            context.sendMsg("资源更新完成，本次共更新${downloadCheck.second}个资源")
            OtherUtil.fileCount = 0

//            // 尝试更新卡池数据
//            UpdateGachaResources().getDataMain()
//
//            // 重新初始化原神相关数据
//            InitGenShinData.initGachaLogData()

            // 发布资源更新事件
            applicationContext.publishEvent(ResourceUpdateEvent(this))
        }
    }

    @GetMapping("/tttt")
    fun tttt(): String {
        applicationContext.publishEvent(ResourceUpdateEvent(this))
        return "tttt"
    }

    @EventListener
    fun  ffff(event: ResourceUpdateEvent){
        println("ffffff")
    }

    @AParameter
    @Executor(action = "清除缓存")
    fun deleteCache(context: BotUtils.Context) {
        forceDeleteCache("resources/imageCache")
        context.sendMsg("已完成缓存清理")
    }

    /**
     * 启动时资源自检
     *
     */
    @PostConstruct
    fun resourcesCheck() {
        val helpFile = File(HELP_JSON)

        if (!helpFile.exists()) {
            // 文件不存在时，使用阻塞方式下载资源
            runBlocking {
                val folderPath = RESOURCES_PATH
                val (success, updatedCount) = otherUtil.downloadFolderFromGitHub(
                    owner,
                    repoName,
                    folderPath,
                    folderPath,
                    accessToken
                )
                if (success) {
                    logInfo("资源初始化更新完成，本次共更新${updatedCount}个资源")
                    OtherUtil.fileCount = 0
                } else logError("资源更新失败")
            }
        } else {
            // 文件存在时，使用协程异步方式下载资源
            scope.launch {
                val folderPath = RESOURCES_PATH
                repeat(10) { attempt -> // 重试10次
                    val (success, updatedCount) = otherUtil.downloadFolderFromGitHub(
                        owner,
                        repoName,
                        folderPath,
                        folderPath,
                        accessToken
                    )
                    if (success) {
                        logInfo("资源初始化更新完成，本次共更新${updatedCount}个资源")
                        OtherUtil.fileCount = 0
                        return@launch // 下载成功，结束协程
                    } else if (attempt < 9) { // 未到最后一次尝试，记录日志
                        logWarn("资源更新失败,进行自动重试...")
                    }
                }
                // 10次尝试均失败，记录错误日志
                logError("资源更新失败，已达最大重试次数")
            }
        }
    }

    @Scheduled(cron = "0 30 2 * * ?")
    fun timingUpdateResources() {
        logInfo("定时更新资源启动")
        val folderPath = RESOURCES_PATH
        runBlocking {
            val downloadCheck = otherUtil.downloadFolderFromGitHub(
                owner,
                repoName,
                folderPath,
                folderPath,
                accessToken
            )

            if (!downloadCheck.first) {
                logWarn("资源更新失败,自动跳过此资源更新")
                return@runBlocking
            }
            logInfo("资源更新完成，本次共更新${downloadCheck.second}个资源")
            OtherUtil.fileCount = 0

//            // 尝试更新卡池数据
//            UpdateGachaResources().getDataMain()
//
//            // 重新初始化原神相关数据
//            InitGenShinData.initGachaLogData()
        }
    }

}