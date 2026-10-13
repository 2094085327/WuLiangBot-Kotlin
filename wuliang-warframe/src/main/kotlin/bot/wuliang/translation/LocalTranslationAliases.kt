package bot.wuliang.translation

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.nio.file.Files
import java.nio.file.Path

/** 仅维护未导出物品的对象路径到游戏语言键的关联，译文由上游语言表提供。 */
internal data class LocalTranslationAliases(val entries: Map<String, String>, val revision: String) {
    companion object {
        val EMPTY = LocalTranslationAliases(emptyMap(), "unavailable")

        fun load(path: Path): LocalTranslationAliases {
            val bytes = Files.readAllBytes(path)
            val root = jacksonObjectMapper().readTree(bytes)
            require(root?.isObject == true) { "翻译关联表必须是 JSON 对象" }
            val entries = linkedMapOf<String, String>()
            root.fields().forEach { (key, value) ->
                require(
                    key.startsWith("/Lotus/", ignoreCase = true) &&
                            !key.startsWith("/Lotus/Language/", ignoreCase = true)
                ) { "关联表来源必须是游戏对象路径：$key" }
                require(value.isTextual && value.asText().startsWith("/Lotus/Language/", ignoreCase = true)) {
                    "关联表目标必须是游戏语言键：$key"
                }
                val normalized = PublicExportIndex.normalize(key).replaceFirst("/lotus/storeitems/", "/lotus/")
                val target = PublicExportIndex.normalize(value.asText())
                val previous = entries.put(normalized, target)
                require(previous == null || previous == target) { "翻译关联表存在冲突：$key" }
            }
            return LocalTranslationAliases(entries, PublicExportStore.gitBlobSha(bytes))
        }
    }
}