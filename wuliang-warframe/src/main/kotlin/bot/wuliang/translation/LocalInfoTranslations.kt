package bot.wuliang.translation

import bot.wuliang.moudles.Info
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.nio.file.Files
import java.nio.file.Path

/** 为科研和1999日历单独维护的词库加载器 */
internal class LocalInfoTranslations private constructor(
    private val entries: Map<String, Info>,
    val revision: String,
) {
    fun info(key: String): Info? = entries[PublicExportIndex.normalize(key)]

    companion object {
        val EMPTY = LocalInfoTranslations(emptyMap(), "unavailable")

        /** 校验标识范围、文本类型和大小写冲突，并以文件内容指纹参与翻译缓存版本计算。 */
        fun load(path: Path, accepts: (String) -> Boolean): LocalInfoTranslations {
            val bytes = Files.readAllBytes(path)
            val root = jacksonObjectMapper().readTree(bytes)
            require(root?.isObject == true && root.size() > 0) { "本地词库必须是非空对象" }
            val entries = linkedMapOf<String, Info>()
            root.fields().forEach { (key, row) ->
                require(accepts(key)) { "本地词库包含不属于该范围的标识：$key" }
                val name = row["value"]
                val desc = row["desc"]
                require(name?.isTextual == true && name.asText().isNotBlank()) { "$key.value 必须是非空文本" }
                require(desc == null || desc.isNull || desc.isTextual) { "$key.desc 必须是文本或 null" }
                val info = Info(name.asText(), desc?.takeUnless { it.isNull }?.asText())
                val previous = entries.put(PublicExportIndex.normalize(key), info)
                require(previous == null || previous == info) { "本地词库存在大小写冲突：$key" }
            }
            return LocalInfoTranslations(entries, PublicExportStore.gitBlobSha(bytes))
        }
    }
}