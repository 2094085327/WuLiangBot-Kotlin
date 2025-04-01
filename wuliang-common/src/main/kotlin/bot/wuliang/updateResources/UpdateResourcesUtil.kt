package bot.wuliang.updateResources

import bot.wuliang.botLog.logUtil.LoggerUtils.logInfo
import bot.wuliang.botLog.logUtil.LoggerUtils.logWarn
import java.io.File

class UpdateResourcesUtil {
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
}