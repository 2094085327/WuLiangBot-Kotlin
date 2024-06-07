package bot.demo.txbot.other

import bot.demo.txbot.common.utils.OtherUtil
import bot.demo.txbot.genShin.util.InitGenShinData
import bot.demo.txbot.genShin.util.UpdateGachaResources
import com.mikuac.shiro.annotation.AnyMessageHandler
import com.mikuac.shiro.annotation.MessageHandlerFilter
import com.mikuac.shiro.annotation.common.Shiro
import com.mikuac.shiro.core.Bot
import com.mikuac.shiro.dto.event.message.AnyMessageEvent
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import pers.wuliang.robot.common.utils.LoggerUtils.logInfo
import pers.wuliang.robot.common.utils.LoggerUtils.logWarn
import java.io.File
import java.util.regex.Matcher


/**
 * @description: 更新所用到的资源文件，包括图片、数据等
 * @author Nature Zero
 * @date 2024/3/12 8:08
 */
@Shiro
@Component
class UpdateResources {
    companion object {
        private var owner: String = ""
        private var repoName: String = ""
        private var accessToken: String? = null
    }

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

    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "更新资源")
    fun updateAll(bot: Bot, event: AnyMessageEvent) {
        bot.sendMsg(event, "正在更新资源，请稍后", false)

        val folderPath = RESOURCES_PATH

        val downloadCheck = otherUtil.downloadFolderFromGitHub(
            owner,
            repoName,
            folderPath,
            folderPath,
            accessToken
        )

        if (!downloadCheck.first) {
            bot.sendMsg(event, "资源更新失败,自动跳过此资源更新,请通知管理员检查错误或稍后再试", false)
            return
        }
        bot.sendMsg(event, "资源更新完成，本次共更新${downloadCheck.second}个资源", false)
        OtherUtil.fileCount = 0

        // 尝试更新卡池数据
        UpdateGachaResources().getDataMain()

        // 重新初始化原神相关数据
        InitGenShinData.initGachaLogData()
    }

    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "清除缓存")
    fun deleteCache(bot: Bot, event: AnyMessageEvent?, matcher: Matcher?) {
        forceDeleteCache("resources/imageCache")
        bot.sendMsg(event, "已完成缓存清理", false)
    }
}