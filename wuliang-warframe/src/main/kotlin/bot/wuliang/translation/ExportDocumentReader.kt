package bot.wuliang.translation

import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.nio.file.Path

/** 对象只保留当前文件的树；大语言表按字段直接合并，不构建 JsonNode 根树。 */
internal class ExportDocumentReader(private val directory: Path, files: List<ExportFile>) {
    private val files = files.associateBy { it.path }
    private val mapper = jacksonObjectMapper()

    fun document(path: String): JsonNode = ExportFileIO.read(files.getValue(path), directory.resolve(path)) { input ->
        requireNotNull(mapper.readTree(input)) { "$path 必须是 JSON 对象或数组" }.also {
            require(it.isContainerNode) { "$path 必须是 JSON 对象或数组" }
        }
    }

    fun dictionary(path: String, result: MutableMap<String, String>) {
        ExportFileIO.read(files.getValue(path), directory.resolve(path)) { input ->
            mergeExportDictionary(path, result) { accept ->
                mapper.factory.createParser(input).use { parser ->
                    require(parser.nextToken() == JsonToken.START_OBJECT) { "$path 字典为空或格式错误" }
                    var count = 0
                    while (parser.nextToken() != JsonToken.END_OBJECT) {
                        require(parser.currentToken == JsonToken.FIELD_NAME) { "$path 字典为空或格式错误" }
                        val key = parser.currentName
                        require(parser.nextToken() == JsonToken.VALUE_STRING) { "$path 字典包含非文本值：$key" }
                        accept(key, parser.text)
                        count++
                    }
                    require(count > 0) { "$path 字典为空或格式错误" }
                }
            }
        }
    }
}

internal fun mergeExportDictionary(
    file: String,
    result: MutableMap<String, String>,
    visit: ((String, String) -> Unit) -> Unit,
) {
    val entries = if (result.isEmpty()) result else linkedMapOf<String, String>()
    visit { key, text ->
        val old = entries.put(PublicExportIndex.normalize(key), text)
        require(old == null || old == text) { "$file 字典存在大小写冲突：$key" }
    }
    entries.entries.removeIf { it.value.isBlank() }
    if (entries !== result) result.putAll(entries)
}

/** 只在构建期间使用，最终字典不持有字符串池；两种语言复用相同标识字符串。 */
internal class CanonicalExportDictionary(
    private val target: MutableMap<String, String>,
    private val canonicalKeys: MutableMap<String, String>,
) : MutableMap<String, String> by target {
    override fun put(key: String, value: String): String? = target.put(canonicalKeys.getOrPut(key) { key }, value)

    override fun putAll(from: Map<out String, String>) {
        from.forEach { (key, value) -> put(key, value) }
    }
}