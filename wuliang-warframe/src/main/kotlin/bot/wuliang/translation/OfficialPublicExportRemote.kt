package bot.wuliang.translation

import bot.wuliang.config.PublicExportPolicy
import bot.wuliang.config.WarframeProperties
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.core.json.JsonReadFeature
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.slf4j.LoggerFactory
import org.tukaani.xz.LZMAInputStream
import java.io.InputStream
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
) : FilePublicExportRemote {
    private val logger = LoggerFactory.getLogger(javaClass)
    private val indexUrl = settings.indexBaseUrl.toHttpUrl()
    private val contentUrl = settings.contentBaseUrl.toHttpUrl()

    // 官方文件的说明中可能包含未转义的换行；写入快照前统一输出合法 JSON。
    private val mapper = jacksonObjectMapper().enable(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS.mappedFeature())

    private data class IndexEntry(val category: String, val location: String)
    private data class CachedExport(val path: Path, val size: Long, val sha: String)

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
        val index = http.read(http.request(url).build(), { status -> downloadError(status, url.toString()) }) { input ->
            LZMAInputStream(input, PublicExportPolicy.LZMA_MEMORY_LIMIT_KIB).use { stream ->
                stream.readNBytes(PublicExportPolicy.MAX_INDEX_BYTES + 1).also {
                    require(it.size <= PublicExportPolicy.MAX_INDEX_BYTES) { "官方 $locale 索引超过允许大小" }
                }
            }.toString(Charsets.UTF_8)
        }
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
        val cached = readCached(asset) ?: downloadCached(asset, entry, locale)
        return ExportFile("official.${entry.category}.$locale.json", cached.sha, cached.size, asset)
    }

    override fun download(commit: String, file: ExportFile): ByteArray {
        val cached = candidate(file)
        return Files.readAllBytes(cached.path).also {
            require(it.size.toLong() == file.size && PublicExportStore.gitBlobSha(it) == file.sha) {
                "官方候选文件校验失败：${file.path}"
            }
        }
    }

    override fun downloadTo(commit: String, file: ExportFile, target: Path) {
        Files.copy(candidate(file).path, target)
    }

    private fun candidate(file: ExportFile): CachedExport {
        val asset = requireNotNull(file.sourceCommit) { "官方文件缺少固定内容地址" }
        val cached = requireNotNull(readCached(asset)) { "官方候选文件已损坏或丢失，请重新检查或更新词库" }
        require(cached.size == file.size && cached.sha == file.sha) { "官方候选文件校验失败：${file.path}" }
        return cached
    }

    /** 只读取元数据并流式校验内容，不为 revision 分配整文件字节数组。 */
    private fun readCached(asset: String): CachedExport? = recoverExportData {
        require(PublicExportPolicy.SHA_PATTERN.matches(asset))
        val path = cache.resolve("$asset.json")
        if (!Files.exists(path)) return@recoverExportData null
        val size = Files.size(path)
        require(size in 2..PublicExportPolicy.MAX_FILE_BYTES.toLong())
        val shaPath = cache.resolve("$asset.sha")
        require(Files.size(shaPath) == 40L)
        val sha = Files.readString(shaPath)
        require(PublicExportPolicy.SHA_PATTERN.matches(sha) && ExportFileIO.blobSha(path) == sha)
        CachedExport(path, size, sha)
    }.onFailure { logger.warn("官方导出缓存校验失败，将重新获取：{}，{}", asset, it.message) }.getOrNull()

    private fun downloadCached(asset: String, entry: IndexEntry, locale: String): CachedExport {
        val temporary = cache.resolve("$asset-${UUID.randomUUID()}.tmp")
        val temporarySha = cache.resolve("$asset-${UUID.randomUUID()}.sha.tmp")
        val url = contentUrl.newBuilder().addPathSegment(entry.location).build()
        try {
            http.read(http.request(url).build(), { status -> downloadError(status, url.toString()) }) { input ->
                normalize(input, temporary, entry.category, locale)
            }
            val size = Files.size(temporary)
            val sha = ExportFileIO.blobSha(temporary)
            Files.writeString(temporarySha, sha)
            val target = cache.resolve("$asset.json")
            Files.move(temporary, target, ATOMIC_MOVE, REPLACE_EXISTING)
            Files.move(temporarySha, cache.resolve("$asset.sha"), ATOMIC_MOVE, REPLACE_EXISTING)
            return CachedExport(target, size, sha)
        } finally {
            Files.deleteIfExists(temporary)
            Files.deleteIfExists(temporarySha)
        }
    }

    /** 数组按对象逐项规范化，保持 Jackson 原有的数值与字符串输出规则。 */
    private fun normalize(input: InputStream, target: Path, category: String, locale: String) {
        val writer = mapper.writer().without(SerializationFeature.FLUSH_AFTER_WRITE_VALUE)
        mapper.factory.createParser(input).use { parser ->
            require(parser.nextToken() == JsonToken.START_OBJECT) { "官方 $category/$locale 缺少对象目录" }
            Files.newOutputStream(target).use { output ->
                mapper.factory.createGenerator(
                    LimitedExportOutputStream(
                        output,
                        PublicExportPolicy.MAX_FILE_BYTES.toLong()
                    )
                ).use { generator ->
                    generator.writeStartObject()
                    var found = false
                    while (parser.nextToken() != JsonToken.END_OBJECT) {
                        require(parser.currentToken == JsonToken.FIELD_NAME) { "官方 $category/$locale 格式错误" }
                        val field = parser.currentName
                        generator.writeFieldName(field)
                        val token = parser.nextToken()
                        require(token != null) { "官方 $category/$locale 格式错误" }
                        if (token == JsonToken.START_ARRAY) {
                            generator.writeStartArray()
                            var count = 0
                            while (parser.nextToken() != JsonToken.END_ARRAY) {
                                require(parser.currentToken != null) { "官方 $category/$locale 格式错误" }
                                writer.writeValue(generator, mapper.readTree<JsonNode>(parser))
                                count++
                            }
                            generator.writeEndArray()
                            if (field == category) {
                                require(count > 0) { "官方 $category/$locale 缺少对象目录" }
                                found = true
                            }
                        } else {
                            require(field != category) { "官方 $category/$locale 缺少对象目录" }
                            writer.writeValue(generator, mapper.readTree<JsonNode>(parser))
                        }
                    }
                    require(found) { "官方 $category/$locale 缺少对象目录" }
                    generator.writeEndObject()
                }
            }
        }
    }

    private fun downloadError(status: Int, url: String) = "官方 Public Export 下载失败：HTTP $status，$url"
}