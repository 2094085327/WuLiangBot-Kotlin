package bot.wuliang.translation

import bot.wuliang.config.PublicExportPolicy

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.StandardOpenOption.CREATE
import java.nio.file.StandardOpenOption.WRITE
import java.security.MessageDigest
import java.util.*

data class ExportFile(val path: String, val sha: String, val size: Long, val sourceCommit: String? = null)
data class ExportRevision(
    val commit: String,
    val files: List<ExportFile>,
    val sources: Map<String, String> = emptyMap()
)

data class ExportManifest(
    val appliedCommit: String,
    val files: List<ExportFile>,
    val sources: Map<String, String> = emptyMap()
)

data class ExportCheck(
    val commit: String,
    val changed: List<String>,
    val removed: List<String>,
    val sources: Map<String, String> = emptyMap()
)

data class AppliedExport(val manifest: ExportManifest, val index: PublicExportIndex)

interface PublicExportRemote {
    /** 固定本次检查的数据源版本，返回文件清单和 blob SHA。 */
    fun revision(): ExportRevision

    /** 按固定提交或内容地址取得单个文件，防止快照混用版本。 */
    fun download(commit: String, file: ExportFile): ByteArray
}

/** 先构建并验证完整快照，再原子替换当前指针，避免读到半更新的词库。 */
class PublicExportStore(
    private val root: Path,
    private val remote: PublicExportRemote,
    private val aliases: Map<String, String> = emptyMap(),
) {
    private val mapper = jacksonObjectMapper()

    private data class Pointer(val directory: String)

    /**
     * 读取当前指针指向的成功快照，逐文件校验后重建索引。
     * 尚无指针时返回 null；指针、清单或内容损坏时抛出异常。
     */
    fun load(): AppliedExport? {
        val directory = currentDirectory() ?: return null
        val manifest = readManifest(directory)
        validateRevision(ExportRevision(manifest.appliedCommit, manifest.files, manifest.sources))
        val documents = manifest.files.associate { file ->
            val bytes = Files.readAllBytes(directory.resolve(file.path))
            validateBytes(file, bytes)
            file.path to mapper.readTree(bytes)
        }
        return AppliedExport(manifest, PublicExportIndex(documents, aliases))
    }

    /**
     * 在文件锁保护下比较远端版本与本地内容，只返回差异清单。
     */
    fun check(): ExportCheck = locked {
        val revision = remote.revision().also(::validateRevision)
        compare(revision, runCatching { currentDirectory() }.getOrNull())
    }

    /**
     * 固定远端提交，复用校验通过的未变文件，只下载变化或损坏的文件。
     * 全部内容及索引验证成功后才发布指针；返回检查结果和完整的新快照。
     */
    fun update(): Pair<ExportCheck, AppliedExport> = locked {
        val revision = remote.revision().also(::validateRevision)
        val current = runCatching { currentDirectory() }.getOrNull()
        val check = compare(revision, current)
        if (check.changed.isEmpty() && check.removed.isEmpty()) {
            val loaded = runCatching { load() }.getOrNull()
            if (loaded != null && loaded.manifest.appliedCommit == revision.commit) return@locked check to loaded
        }
        // 新目录仅用于构建候选快照；校验和指针发布成功前，查询仍使用旧快照。
        val directory = root.resolve("snapshots").resolve("${revision.commit}-${UUID.randomUUID()}")
        Files.createDirectories(directory)
        val documents = revision.files.associate { file ->
            val bytes = if (file.path !in check.changed && current != null) {
                Files.readAllBytes(current.resolve(file.path))
            } else {
                remote.download(revision.commit, file)
            }
            validateBytes(file, bytes)
            val document = mapper.readTree(bytes)
            require(document != null && document.isContainerNode) { "${file.path} 必须是 JSON 对象或数组" }
            Files.write(directory.resolve(file.path), bytes)
            file.path to document
        }
        val index = PublicExportIndex(documents, aliases)
        val manifest = ExportManifest(revision.commit, revision.files, revision.sources)
        Files.write(directory.resolve("manifest.json"), mapper.writeValueAsBytes(manifest))
        val temporaryPointer = root.resolve("current-${UUID.randomUUID()}.tmp")
        Files.write(temporaryPointer, mapper.writeValueAsBytes(Pointer(directory.fileName.toString())))
        // 文件系统不支持原子替换时直接失败，不能降级为可能留下不完整指针的普通移动。
        Files.move(temporaryPointer, root.resolve("current.json"), ATOMIC_MOVE, REPLACE_EXISTING)
        check to AppliedExport(manifest, index)
    }

    /**
     * 同时比较文件元数据与实际字节；即使 SHA 清单未变，缺失或损坏的文件也需修复。
     */
    private fun compare(revision: ExportRevision, current: Path?): ExportCheck {
        val previous = current?.let { runCatching { readManifest(it) }.getOrNull() }
        val oldFiles = previous?.files.orEmpty().associateBy { it.path }
        val changed = revision.files.filter { file ->
            // 来源提交变化不代表文件内容变化；相同 blob 继续复用，避免重下整个语言表。
            oldFiles[file.path]?.let { it.sha != file.sha || it.size != file.size } != false || current == null || !runCatching {
                validateBytes(file, Files.readAllBytes(current.resolve(file.path)))
            }.isSuccess
        }.map { it.path }
        return ExportCheck(
            revision.commit,
            changed,
            oldFiles.keys.minus(revision.files.map { it.path }.toSet()).sorted(),
            revision.sources,
        )
    }

    /**
     * 解析受限格式的快照目录名，禁止指针携带任意相对路径。
     */
    private fun currentDirectory(): Path? {
        val pointer = root.resolve("current.json")
        if (!Files.exists(pointer)) return null
        val name = mapper.readValue<Pointer>(Files.readAllBytes(pointer)).directory
        require(PublicExportPolicy.SNAPSHOT_DIRECTORY_PATTERN.matches(name)) { "无效的词库快照指针" }
        return root.resolve("snapshots").resolve(name)
    }

    private fun readManifest(directory: Path): ExportManifest =
        mapper.readValue(Files.readAllBytes(directory.resolve("manifest.json")))

    /**
     * 通过非阻塞文件锁互斥同一存储目录的检查和更新；锁被占用时立即提示重试。
     */
    private fun <T> locked(action: () -> T): T {
        Files.createDirectories(root)
        return FileChannel.open(root.resolve("sync.lock"), CREATE, WRITE).use { channel ->
            val lock = runCatching { channel.tryLock() }.getOrNull()
                ?: error("Warframe 词库正在检查或更新，请稍后重试")
            lock.use { action() }
        }
    }

    companion object {
        val requiredFiles = PublicExportPolicy.REQUIRED_PLUS_FILES
        val languageFiles = PublicExportPolicy.COMPLETE_LANGUAGE_FILES

        /** 仅接收根目录下的社区和官方导出 JSON、中英文字典及完整语言表。 */
        fun accepts(path: String): Boolean = acceptsPlus(path) || path in languageFiles || acceptsOfficial(path)

        fun acceptsOfficial(path: String): Boolean = PublicExportPolicy.OFFICIAL_FILE_PATTERN.matches(path)

        /** 无官方文件的旧快照可以读取；新快照必须包含完整、成对的核心分类。 */
        fun validateOfficialFiles(paths: Set<String>) {
            val official = paths.filter(::acceptsOfficial).toSet()
            if (official.isEmpty()) return
            PublicExportPolicy.LOCALES.forEach { locale ->
                require(official.containsAll(PublicExportPolicy.REQUIRED_OFFICIAL_CATEGORIES.map { "official.$it.$locale.json" })) {
                    "官方 $locale 快照缺少必要对象导出"
                }
            }
            val en = official.filter { it.endsWith(".en.json") }.map { it.removeSuffix(".en.json") }.toSet()
            val zh = official.filter { it.endsWith(".zh.json") }.map { it.removeSuffix(".zh.json") }.toSet()
            require(en == zh) { "官方中英文对象导出分类不一致" }
        }

        fun acceptsPlus(path: String): Boolean = PublicExportPolicy.PLUS_FILE_PATTERN.matches(path)

        /** 校验提交、必需文件、重复路径、文件大小和 blob SHA，拒绝不完整的远端清单。 */
        fun validateRevision(revision: ExportRevision) {
            require(PublicExportPolicy.SHA_PATTERN.matches(revision.commit)) { "无效的上游 commit" }
            val paths = revision.files.map { it.path }.toSet()
            require(paths.containsAll(requiredFiles)) { "上游快照缺少必要文件" }
            require(paths.size == revision.files.size) { "上游文件清单重复" }
            validateOfficialFiles(paths)
            require(
                paths.intersect(languageFiles).isEmpty() || paths.containsAll(languageFiles)
            ) { "完整语言表必须同时包含中文和英文" }
            require(revision.sources.values.all { PublicExportPolicy.SHA_PATTERN.matches(it) }) { "无效的数据源 commit" }
            require(revision.files.all {
                accepts(it.path) && PublicExportPolicy.SHA_PATTERN.matches(it.sha) &&
                        (it.sourceCommit == null || PublicExportPolicy.SHA_PATTERN.matches(it.sourceCommit)) && it.size in 2..PublicExportPolicy.MAX_FILE_BYTES.toLong()
            }) {
                "上游文件路径、大小或 blob SHA 无效"
            }
        }

        /** 按 Git blob 规则把类型、字节长度和空字节头部一并计算，不能直接对文件做普通 SHA-1。 */
        fun gitBlobSha(bytes: ByteArray): String {
            val digest = MessageDigest.getInstance("SHA-1")
            digest.update("blob ${bytes.size}\u0000".toByteArray(Charsets.UTF_8))
            return digest.digest(bytes).joinToString("") { "%02x".format(it) }
        }

        private fun validateBytes(file: ExportFile, bytes: ByteArray) {
            require(bytes.size.toLong() == file.size && gitBlobSha(bytes) == file.sha) { "${file.path} 完整性校验失败" }
        }
    }
}