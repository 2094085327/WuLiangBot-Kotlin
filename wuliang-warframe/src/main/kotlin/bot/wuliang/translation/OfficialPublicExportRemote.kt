package bot.wuliang.translation

import bot.wuliang.config.PublicExportPolicy
import bot.wuliang.config.WarframeProperties
import com.fasterxml.jackson.core.json.JsonReadFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.slf4j.LoggerFactory
import org.tukaani.xz.LZMAInputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.util.*

/** 固定官方中英文索引中的内容地址，将规范化后的 JSON 纳入快照。 */
internal class OfficialPublicExportRemote(
    private val cache: Path,
    settings: WarframeProperties.OfficialExport,
    private val http: PublicExportHttpClient,
) : PublicExportRemote {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val indexUrl = settings.indexBaseUrl.toHttpUrl()
    private val contentUrl = settings.contentBaseUrl.toHttpUrl()

    // 官方文件的说明中可能包含未转义的换行；写入快照前统一输出合法 JSON。
    private val mapper = jacksonObjectMapper().enable(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS.mappedFeature())

    private data class IndexEntry(val category: String, val location: String)
    private data class CachedExport(val bytes: ByteArray, val sha: String)

    override fun revision(): ExportRevision {
        Files.createDirectories(cache)
        val sources = linkedMapOf<String, String>()
        val files = PublicExportPolicy.LOCALES.flatMap { locale ->
            val entries = readIndex(locale)
            sources["official-$locale"] = PublicExportStore.gitBlobSha(
                entries
                    .joinToString("\n") { it.location }.toByteArray(Charsets.UTF_8)
            )
            entries.map { exportFile(it, locale) }
        }
        val version = PublicExportStore.gitBlobSha(sources.values.joinToString(":").toByteArray(Charsets.UTF_8))
        return ExportRevision(version, files.sortedBy { it.path }, sources)
    }

    private fun readIndex(locale: String): List<IndexEntry> {
        // 索引每次重新获取，只有固定内容地址的导出文件允许持久缓存。
        val url = indexUrl.newBuilder().addPathSegment("index_$locale.txt.lzma").build()
        val compressed = get(url.toString())
        val index = LZMAInputStream(compressed.inputStream(), PublicExportPolicy.LZMA_MEMORY_LIMIT_KIB).use { stream ->
            stream.readNBytes(PublicExportPolicy.MAX_INDEX_BYTES + 1).also {
                require(it.size <= PublicExportPolicy.MAX_INDEX_BYTES) { "官方 $locale 索引超过允许大小" }
            }
        }.toString(Charsets.UTF_8)
        val entries = index.lineSequence().map(String::trim).mapNotNull { location ->
            val match = PublicExportPolicy.OFFICIAL_INDEX_PATTERN.matchEntire(location)
            match?.takeIf { it.groupValues[2] == locale }?.let { IndexEntry(it.groupValues[1], location) }
        }.sortedBy { it.category }.toList()
        val categories = entries.map { it.category }.toSet()
        require(categories.size == entries.size) { "官方 $locale 索引存在重复文件" }
        require(categories.containsAll(PublicExportPolicy.REQUIRED_OFFICIAL_CATEGORIES)) { "官方 $locale 索引缺少必要对象导出" }
        return entries
    }

    private fun exportFile(entry: IndexEntry, locale: String): ExportFile {
        // 保持已有缓存地址的指纹格式，升级时仍可复用本地副本。
        val asset = PublicExportStore.gitBlobSha("$contentUrl${entry.location}".toByteArray(Charsets.UTF_8))
        val cached = readCached(asset) ?: run {
            val url = contentUrl.newBuilder().addPathSegment(entry.location).build()
            val document = mapper.readTree(get(url.toString()))
            require(
                document?.isObject == true && document.path(entry.category).isArray && document.path(entry.category)
                    .size() > 0
            ) {
                "官方 ${entry.category}/$locale 缺少对象目录"
            }
            val bytes = mapper.writeValueAsBytes(document)
            CachedExport(bytes, PublicExportStore.gitBlobSha(bytes)).also { writeCached(asset, it) }
        }
        return ExportFile("official.${entry.category}.$locale.json", cached.sha, cached.bytes.size.toLong(), asset)
    }

    override fun download(commit: String, file: ExportFile): ByteArray {
        val asset = requireNotNull(file.sourceCommit) { "官方文件缺少固定内容地址" }
        val cached = requireNotNull(readCached(asset)) { "官方候选文件已损坏或丢失，请重新检查或更新词库" }
        require(cached.bytes.size.toLong() == file.size && cached.sha == file.sha) { "官方候选文件校验失败：${file.path}" }
        return cached.bytes
    }

    /** 校验失败时重新下载 */
    private fun readCached(asset: String): CachedExport? = runCatching {
        require(PublicExportPolicy.SHA_PATTERN.matches(asset))
        val path = cache.resolve("$asset.json")
        if (!Files.exists(path)) return@runCatching null
        require(Files.size(path) in 2..PublicExportPolicy.MAX_FILE_BYTES.toLong())
        val bytes = Files.readAllBytes(path)
        val sha = Files.readString(cache.resolve("$asset.sha"))
        require(PublicExportStore.gitBlobSha(bytes) == sha)
        CachedExport(bytes, sha)
    }.onFailure { logger.warn("官方导出缓存校验失败，将重新获取：{}，{}", asset, it.message) }.getOrNull()

    private fun writeCached(asset: String, cached: CachedExport) {
        require(cached.bytes.size <= PublicExportPolicy.MAX_FILE_BYTES) { "官方导出超过允许大小" }
        val temporary = cache.resolve("$asset-${UUID.randomUUID()}.tmp")
        Files.write(temporary, cached.bytes)
        Files.move(temporary, cache.resolve("$asset.json"), ATOMIC_MOVE, REPLACE_EXISTING)
        Files.writeString(cache.resolve("$asset.sha"), cached.sha)
    }

    private fun get(url: String): ByteArray = http.get(http.request(url.toHttpUrl()).build()) { status ->
        "官方 Public Export 下载失败：HTTP $status，$url"
    }
}