package bot.wuliang.file

import org.springframework.stereotype.Component
import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING


/**
 * @description: 文件工具类
 * @author Nature Zero
 * @date 2025/6/28 17:04
 */
@Component
class FileUtils {
    /**
     * 按给定顺序将分块写入同目录临时文件，全部成功后再替换目标，避免旧尾部残留。
     * 不支持原子移动时使用普通替换；复制分块失败不会触碰原目标文件。
     * @param targetFile 目标文件，父目录由调用方提前创建
     * @param chunkFiles 已按数字序号排序并校验过的分块文件
     */
    @Throws(IOException::class)
    fun mergeFiles(targetFile: File, chunkFiles: Array<out File>) {
        val target = targetFile.toPath().toAbsolutePath()
        val temporary = Files.createTempFile(target.parent, ".upload-", ".tmp")
        try {
            Files.newOutputStream(temporary).use { output ->
                for (chunk in chunkFiles) {
                    Files.newInputStream(chunk.toPath(), NOFOLLOW_LINKS).use { it.copyTo(output) }
                }
            }
            try {
                Files.move(temporary, target, ATOMIC_MOVE, REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(temporary, target, REPLACE_EXISTING)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    /**
     * 递归清理超过 24 小时的临时文件，不跟随符号链接或指向外部的目录联接。
     * 此方法保留目录本身；合并成功后的即时清理由上传控制器负责。
     *
     * @param tempFile 允许清理的临时目录
     */
    fun deleteTempDirectory(tempFile: File) {
        if (!Files.isDirectory(tempFile.toPath(), NOFOLLOW_LINKS) || Files.isSymbolicLink(tempFile.toPath())) return

        val files = tempFile.listFiles() ?: return

        for (file in files) {
            if (Files.isSymbolicLink(file.toPath()) ||
                !file.toPath().toRealPath().startsWith(tempFile.toPath().toRealPath())
            ) continue
            if (file.isDirectory) {
                deleteTempDirectory(file)
            } else {
                val lastModifiedTime = file.lastModified()
                val currentTime = System.currentTimeMillis()
                val timeDifference = currentTime - lastModifiedTime
                val hoursDifference = timeDifference / (60 * 60 * 1000)

                if (hoursDifference >= 24) {
                    file.delete()
                }
            }
        }
    }

}