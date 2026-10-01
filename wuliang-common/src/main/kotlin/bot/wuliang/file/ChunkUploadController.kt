package bot.wuliang.file

import bot.wuliang.botLog.logUtil.LoggerUtils.logError
import bot.wuliang.config.CommonConfig.FILE_CACHE_PATH
import bot.wuliang.exception.RespBean
import bot.wuliang.exception.RespBeanEnum
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*

/**
 * 文件分块上传。
 */
@RestController
@RequestMapping("/upload")
class ChunkUploadController internal constructor(
    private val fileUtils: FileUtils,
    cacheDirectory: Path
) {
    @Autowired
    constructor(fileUtils: FileUtils) : this(fileUtils, Paths.get(FILE_CACHE_PATH))

    private val cacheRoot = cacheDirectory.toAbsolutePath().normalize()
    private val md5Pattern = Regex("[0-9a-fA-F]{32}")
    private val uploadIdPattern = Regex("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")
    private val chunkPattern = Regex("""chunk_(0|[1-9]\d*)\.tmp""")
    private val reservedFileName = Regex("""(?i)(CON|PRN|AUX|NUL|COM[1-9]|LPT[1-9])(\..*)?""")

    /**
     * 定期清理过期分块；先校验缓存路径，避免清理任务沿外部链接误删文件。
     */
    @Scheduled(fixedRate = 24 * 60 * 60 * 1000)
    fun cleanTempFiles() {
        fileUtils.deleteTempDirectory(checkedPath("chunks").toFile())
    }

    /**
     * 校验文件名与 MD5 标识后创建独立上传目录，返回后续请求使用的上传 ID。
     * @param fileName 不含目录、驱动器前缀或 Windows 特殊字符的目标文件名
     * @param fileMd5 32 位十六进制文件标识，用于组织分块目录，不代替内容校验
     */
    @PostMapping("/init")
    fun initUpload(
        @RequestParam fileName: String,
        @RequestParam fileMd5: String
    ): RespBean<out String> = try {
        validateFileName(fileName)
        val uploadId = UUID.randomUUID().toString()
        Files.createDirectories(chunkDirectory(fileMd5, uploadId))
        RespBean.success(uploadId)
    } catch (_: IllegalArgumentException) {
        RespBean.error(RespBeanEnum.BIND_ERROR)
    } catch (e: IOException) {
        logError("文件上传操作失败", e)
        RespBean.error(RespBeanEnum.ERROR)
    }

    /**
     * 把分块写入已初始化的上传目录；拒绝负索引和越界路径。
     * @param uploadId 初始化接口返回的 UUID
     * @param index 从 0 开始的分块序号，合并时必须连续
     */
    @PostMapping("/chunk")
    fun uploadChunk(
        @RequestParam chunk: MultipartFile,
        @RequestParam uploadId: String,
        @RequestParam fileMd5: String,
        @RequestParam index: Int
    ): RespBean<out String> = try {
        require(index >= 0)
        val directory = chunkDirectory(fileMd5, uploadId)
        if (!Files.isDirectory(directory, NOFOLLOW_LINKS)) {
            RespBean.error(RespBeanEnum.CHUNKS_ERROR)
        } else {
            val destination = checkedPath("chunks", directory.fileName.toString(), "chunk_$index.tmp")
            chunk.transferTo(destination)
            RespBean.success()
        }
    } catch (_: IllegalArgumentException) {
        RespBean.error(RespBeanEnum.BIND_ERROR)
    } catch (e: IOException) {
        logError("文件上传操作失败", e)
        RespBean.error(RespBeanEnum.ERROR)
    }

    /**
     * 检查分块编号从 0 起连续，并按数字顺序合并到最终目录。
     * 完整写入后才替换目标文件并清理分块；分块复制失败时保留已有目标文件。
     */
    @PostMapping("/merge")
    fun mergeChunks(
        @RequestParam fileName: String,
        @RequestParam uploadId: String,
        @RequestParam fileMd5: String
    ): RespBean<out String> = try {
        validateFileName(fileName)
        val directory = chunkDirectory(fileMd5, uploadId)
        val chunks = uploadedChunks(directory)
        if (chunks.isEmpty() || chunks.map { it.first } != chunks.indices.toList()) {
            RespBean.error(RespBeanEnum.CHUNKS_ERROR)
        } else {
            val destination = checkedPath("final", fileName)
            Files.createDirectories(destination.parent)
            fileUtils.mergeFiles(destination.toFile(), chunks.map { it.second.toFile() }.toTypedArray())
            // 仅在合并成功后删除已使用的分块；清理失败交由定时任务重试。
            try {
                chunks.forEach { Files.deleteIfExists(it.second) }
                Files.deleteIfExists(directory)
            } catch (_: IOException) {
                // 已发布的文件仍然有效，不把清理失败当成合并失败。
            }
            RespBean.success()
        }
    } catch (_: IllegalArgumentException) {
        RespBean.error(RespBeanEnum.BIND_ERROR)
    } catch (e: IOException) {
        logError("文件上传操作失败", e)
        RespBean.error(RespBeanEnum.ERROR)
    }

    /**
     * 返回已收到的分块序号，供客户端续传；目录不存在时返回空列表。
     * 发现不合法的分块名称或路径时返回校验错误，不把它们当成已上传内容。
     */
    @GetMapping("/check/{fileMd5}/{uploadId}")
    fun getUploadedChunks(
        @PathVariable fileMd5: String,
        @PathVariable uploadId: String
    ): RespBean<out List<Int>> = try {
        RespBean.success(uploadedChunks(chunkDirectory(fileMd5, uploadId)).map { it.first })
    } catch (_: IllegalArgumentException) {
        RespBean.error(RespBeanEnum.BIND_ERROR, emptyList())
    } catch (e: IOException) {
        logError("文件上传操作失败", e)
        RespBean.error(RespBeanEnum.ERROR, emptyList())
    }

    /**
     * 只接受单个文件名，并按 Windows 规则拒绝设备名、备用数据流及尾随点或空格。
     */
    private fun validateFileName(fileName: String) {
        require(fileName.isNotBlank() && fileName != "." && fileName != "..")
        require(fileName.none { it.code < 32 || it in "/\\:\"<>|?*" })
        require(!fileName.endsWith('.') && !fileName.endsWith(' '))
        require(!reservedFileName.matches(fileName))
    }

    /**
     * 严格校验并统一标识大小写，再解析上传目录，防止标识被当成路径片段。
     */
    private fun chunkDirectory(fileMd5: String, uploadId: String): Path {
        require(md5Pattern.matches(fileMd5) && uploadIdPattern.matches(uploadId))
        return checkedPath("chunks", "${fileMd5.lowercase()}_${uploadId.lowercase()}")
    }

    /**
     * 读取并关闭目录流，严格解析普通分块文件的数字序号，拒绝目录、链接及溢出编号。
     */
    private fun uploadedChunks(directory: Path): List<Pair<Int, Path>> {
        if (!Files.exists(directory, NOFOLLOW_LINKS)) return emptyList()
        return Files.list(directory).use { paths ->
            paths.map { path ->
                val index = chunkPattern.matchEntire(path.fileName.toString())
                    ?.groupValues?.get(1)?.toIntOrNull()
                require(index != null && Files.isRegularFile(path, NOFOLLOW_LINKS))
                val checked = checkedPath("chunks", directory.fileName.toString(), path.fileName.toString())
                index to checked
            }.toList().sortedBy { it.first }
        }
    }

    /** 同时校验规范化路径和真实路径，拒绝指向缓存目录外的链接/Windows 联接。 */
    private fun checkedPath(vararg parts: String): Path {
        var path = cacheRoot
        for (part in parts) {
            path = path.resolve(part).normalize()
            require(path.startsWith(cacheRoot) && path != cacheRoot)
            if (Files.exists(path, NOFOLLOW_LINKS)) {
                require(!Files.isSymbolicLink(path) && path.toRealPath().startsWith(cacheRoot.toRealPath()))
            }
        }
        return path
    }
}