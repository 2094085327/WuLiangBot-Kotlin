package bot.wuliang.restart

import bot.wuliang.botLog.logUtil.LoggerUtils.logWarn
import bot.wuliang.config.CommonConfig.FILE_CACHE_PATH
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.multipart.MultipartFile
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.annotation.PreDestroy

@Component
class JarUploadManager(private val restartManager: RestartManager) {
    companion object {
        const val CHUNK_SIZE = 1024 * 1024
        const val MAX_FILE_SIZE = 1024L * 1024 * 1024
        private const val IDLE_MILLIS = 15 * 60 * 1000L
        private const val MAX_RECORDS = 64
        private const val MAX_ACTIVE = 3
    }

    data class Initialized(val uploadId: String, val chunkSize: Int, val chunkCount: Int)
    data class Uploaded(val fileName: String, val size: Long, val md5: String)

    private class Upload(val id: String, val name: String, val size: Long, val count: Int, val directory: Path) {
        val cancelled = AtomicBoolean(false)
        val received = BooleanArray(count)
        @Volatile var lastActivity = System.currentTimeMillis()
        @Volatile var result: Uploaded? = null
    }

    private val workingDirectory = Path.of(System.getProperty("user.dir")).toRealPath()
    private val cacheRoot = workingDirectory.resolve(FILE_CACHE_PATH).resolve("jar-uploads").normalize()
    private val uploads = ConcurrentHashMap<String, Upload>()
    private val initializationLock = Any()
    private val mergeLock = Any()
    private val closed = AtomicBoolean(false)

    fun initialize(fileName: String, size: Long): Initialized = synchronized(initializationLock) {
        check(!closed.get()) { "服务正在关闭，请稍后重新上传" }
        require(size in 1..MAX_FILE_SIZE) { "Jar 文件大小必须大于0且不超过1024MB" }
        restartManager.checkUploadTarget(fileName)
        check(uploads.size < MAX_RECORDS && uploads.values.count { it.result == null } < MAX_ACTIVE) {
            "上传任务较多，请等待其他任务完成后重试"
        }
        val id = UUID.randomUUID().toString()
        val directory = checkedDirectory(cacheRoot.resolve(id), create = true)
        val count = ((size + CHUNK_SIZE - 1) / CHUNK_SIZE).toInt()
        uploads[id] = Upload(id, fileName, size, count, directory)
        Initialized(id, CHUNK_SIZE, count)
    }

    fun uploadChunk(id: String, index: Int, file: MultipartFile) {
        val upload = upload(id)
        synchronized(upload) {
            checkActive(upload)
            require(index in 0 until upload.count) { "切片编号无效" }
            val expected = minOf(CHUNK_SIZE.toLong(), upload.size - index.toLong() * CHUNK_SIZE)
            require(file.size == expected) { "切片大小不正确，请重新上传" }
            checkedDirectory(upload.directory)
            val temporary = Files.createTempFile(upload.directory, "part-", ".tmp")
            try {
                file.inputStream.use { input ->
                    Files.newOutputStream(temporary).buffered().use { output ->
                        val buffer = ByteArray(8192)
                        var written = 0L
                        while (true) {
                            checkActive(upload)
                            val read = input.read(buffer)
                            if (read < 0) break
                            written += read
                            require(written <= expected) { "切片内容超出声明大小" }
                            output.write(buffer, 0, read)
                        }
                        require(written == expected) { "切片内容不完整" }
                    }
                }
                checkActive(upload)
                // 同一片重传时替换，不追加。完整写入后才标记收到。
                Files.move(temporary, upload.directory.resolve("$index.part"), ATOMIC_MOVE, REPLACE_EXISTING)
                upload.received[index] = true
                upload.lastActivity = System.currentTimeMillis()
            } finally {
                Files.deleteIfExists(temporary)
            }
        }
    }

    fun merge(id: String, md5: String): Uploaded = synchronized(mergeLock) {
        require(Regex("[0-9a-fA-F]{32}").matches(md5)) { "文件校验值无效" }
        val upload = upload(id)
        synchronized(upload) {
            upload.result?.let {
                require(it.md5.equals(md5, ignoreCase = true)) { "文件校验值与已完成任务不一致" }
                return@synchronized it
            }
            checkActive(upload)
            check(upload.received.all { it }) { "切片尚未收齐，请重新上传缺失内容" }
            checkedDirectory(upload.directory)
            restartManager.checkUploadTarget(upload.name)
            val staging = Files.createTempFile(workingDirectory, ".jar-upload-", ".tmp")
            try {
                val digest = MessageDigest.getInstance("MD5")
                var written = 0L
                DigestOutputStream(Files.newOutputStream(staging).buffered(), digest).use { output ->
                    val buffer = ByteArray(8192)
                    for (index in 0 until upload.count) {
                        val part = upload.directory.resolve("$index.part")
                        val expected = minOf(CHUNK_SIZE.toLong(), upload.size - index.toLong() * CHUNK_SIZE)
                        require(Files.isRegularFile(part, NOFOLLOW_LINKS) && Files.size(part) == expected) {
                            "切片缺失或大小异常，请重新上传"
                        }
                        Files.newInputStream(part, NOFOLLOW_LINKS).use { input ->
                            while (true) {
                                checkActive(upload)
                                val read = input.read(buffer)
                                if (read < 0) break
                                written += read
                                require(written <= upload.size) { "合并内容超出声明大小" }
                                output.write(buffer, 0, read)
                            }
                        }
                    }
                }
                require(written == upload.size) { "合并后的文件大小不正确" }
                val actual = digest.digest().joinToString("") { "%02x".format(it.toInt() and 0xff) }
                require(actual.equals(md5, ignoreCase = true)) { "文件校验失败，请重新上传" }
                checkActive(upload)
                val name = restartManager.publishUploadedJar(staging, upload.name)
                val result = Uploaded(name, written, actual)
                upload.result = result
                upload.lastActivity = System.currentTimeMillis()
                cleanup(upload)
                result
            } finally {
                Files.deleteIfExists(staging)
            }
        }
    }

    fun cancel(id: String): Uploaded? {
        validateId(id)
        val upload = uploads[id] ?: return null
        upload.cancelled.set(true)
        return synchronized(upload) {
            // 发布已完成时只返回实际结果
            val result = upload.result
            if (cleanup(upload)) {
                if (result == null) uploads.remove(id, upload)
            }
            result
        }
    }

    @Scheduled(fixedDelay = 60_000)
    fun cleanupExpired() {
        val now = System.currentTimeMillis()
        for (upload in uploads.values) {
            if (now - upload.lastActivity < IDLE_MILLIS) continue
            synchronized(upload) {
                if (now - upload.lastActivity < IDLE_MILLIS) return@synchronized
                upload.cancelled.set(true)
                if (cleanup(upload)) uploads.remove(upload.id, upload)
            }
        }
    }

    @PreDestroy
    fun close() {
        val pending = synchronized(initializationLock) {
            closed.set(true)
            uploads.values.toList()
        }
        pending.forEach { it.cancelled.set(true) }
        pending.forEach { upload -> synchronized(upload) { cleanup(upload) } }
        uploads.clear()
    }

    private fun upload(id: String): Upload {
        validateId(id)
        return checkNotNull(uploads[id]) { "上传任务不存在或已过期，请重新上传" }
    }

    private fun validateId(id: String) {
        require(Regex("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}").matches(id)) { "上传任务编号无效" }
    }

    private fun checkActive(upload: Upload) {
        check(!upload.cancelled.get() && upload.result == null && uploads[upload.id] === upload) {
            "上传任务已取消或已完成"
        }
    }

    private fun checkedDirectory(directory: Path, create: Boolean = false): Path {
        require(directory.startsWith(cacheRoot) && directory != workingDirectory)
        var current = workingDirectory
        for (part in workingDirectory.relativize(directory)) {
            current = current.resolve(part)
            if (!Files.exists(current, NOFOLLOW_LINKS) && create) Files.createDirectory(current)
            require(Files.isDirectory(current, NOFOLLOW_LINKS) && current.toRealPath() == current) {
                "上传缓存路径无效或包含链接"
            }
        }
        return directory
    }

    private fun cleanup(upload: Upload): Boolean {
        return try {
            if (!Files.exists(upload.directory, NOFOLLOW_LINKS)) return true
            checkedDirectory(upload.directory)
            // 只清理本任务生成的明确编号文件
            for (index in 0 until upload.count) {
                val part = upload.directory.resolve("$index.part")
                if (Files.exists(part, NOFOLLOW_LINKS)) {
                    require(Files.isRegularFile(part, NOFOLLOW_LINKS)) { "切片路径异常，已停止清理" }
                    Files.delete(part)
                }
            }
            Files.delete(upload.directory)
            true
        } catch (e: Exception) {
            logWarn("Jar 上传临时文件清理失败，稍后重试：{}", e.message)
            false
        }
    }
}