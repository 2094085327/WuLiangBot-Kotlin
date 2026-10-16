package bot.wuliang.translation

import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.nio.file.Path

/** 对象只保留当前文件的树；大语言表按字段直接合并，不构建 JsonNode 根树。 */
internal class ExportDocumentReader(private val directory: Path, files: List<ExportFile>) {
    private val files = files.associateBy { it.path }
    private val mapper = jacksonObjectMapper()

    /** 校验并读取 [path] 对应的 JSON 对象或数组，空内容或非容器数据不作为有效文档。 */
    fun document(path: String): JsonNode = ExportFileIO.read(files.getValue(path), directory.resolve(path)) { input ->
        requireNotNull(mapper.readTree(input)) { "$path 必须是 JSON 对象或数组" }.also {
            require(it.isContainerNode) { "$path 必须是 JSON 对象或数组" }
        }
    }

    /** 流式读取 [path] 的字符串字典并合并到 [result]，避免构建完整 JSON 根树。 */
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

/** 统一字典键的大小写，拒绝同键不同值并清理空文本，再合并到目标字典。 */
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
    /** 使用共用字符串池中的规范键写入目标字典，返回该键原有的值。 */
    override fun put(key: String, value: String): String? = target.put(canonicalKeys.getOrPut(key) { key }, value)

    /** 逐项写入 [from]，使所有键都经过共用字符串池规范化。 */
    override fun putAll(from: Map<out String, String>) {
        from.forEach { (key, value) -> put(key, value) }
    }
}