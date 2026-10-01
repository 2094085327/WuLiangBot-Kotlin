package bot.wuliang.translation

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.nio.file.Files
import java.nio.file.Path

/**
 * 从本地 sortieData.json 加载突击修正的名称与描述。
 * 仅收录 SORTIE_MODIFIER_ 标识，避免将文件中的其他数据引入专项翻译。
 *
 * @property revision 文件内容指纹，参与翻译缓存版本计算。
 */
internal class LocalSortieTranslations private constructor(
    private val names: Map<String, String>,
    private val descriptions: Map<String, String>,
    val revision: String,
) {
    /** 按标识查询修正名称，忽略大小写；未收录时返回 null，由调用方决定回退方式。 */
    fun name(key: String): String? = names[PublicExportIndex.normalize(key)]

    /** 按标识查询修正描述，忽略大小写；未收录时返回 null。 */
    fun description(key: String): String? = descriptions[PublicExportIndex.normalize(key)]

    companion object {
        /** 本地词库不可用时由调用方使用的空词库。 */
        val EMPTY = LocalSortieTranslations(emptyMap(), emptyMap(), "unavailable")

        /**
         * 读取 modifierTypes（名称）和 modifierDescriptions（描述）两个非空对象。
         * 收录的文本必须非空；同一标识的大小写变体仅在文本一致时允许合并。
         * 文件读取、JSON 解析或校验失败时向调用方抛出异常。
         */
        fun load(path: Path): LocalSortieTranslations {
            val bytes = Files.readAllBytes(path)
            val root = jacksonObjectMapper().readTree(bytes)
            fun section(field: String): Map<String, String> {
                val entries = root?.get(field)
                require(entries?.isObject == true && entries.size() > 0) { "sortieData.$field 必须是非空对象" }
                val result = linkedMapOf<String, String>()
                entries.fields().forEach { (key, value) ->
                    if (key.startsWith("SORTIE_MODIFIER_", ignoreCase = true)) {
                        require(
                            value.isTextual && value.asText().isNotBlank()
                        ) { "sortieData.$field.$key 必须是非空文本" }
                        val text = value.asText()
                        // 使用与查询一致的标识规范化规则，并拒绝规范化后出现不同译文。
                        val previous = result.put(PublicExportIndex.normalize(key), text)
                        require(previous == null || previous == text) { "sortieData.$field 存在大小写冲突：$key" }
                    }
                }
                return result
            }
            return LocalSortieTranslations(
                section("modifierTypes"),
                section("modifierDescriptions"),
                // 指纹基于原始文件内容；重新加载文件后，内容变化会反映到缓存版本中。
                PublicExportStore.gitBlobSha(bytes)
            )
        }
    }
}