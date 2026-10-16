package bot.wuliang.translation

/** 单候选直接保存 ID；只有同名多候选时才分配列表，保留候选插入顺序。 */
internal class ExportNameIndex private constructor(private val entries: Map<String, Any>) {
    /** 按规范名称读取候选 ID，保持插入顺序；没有候选时返回空列表。 */
    @Suppress("UNCHECKED_CAST")
    operator fun get(name: String): List<String> = when (val value = entries[name]) {
        null -> emptyList()
        is String -> listOf(value)
        else -> value as List<String>
    }

    /** 对名称索引中的每个候选 ID 执行 [visit]，兼容单候选与多候选的内部存储形式。 */
    @Suppress("UNCHECKED_CAST")
    fun forEachId(visit: (String) -> Unit) {
        entries.values.forEach { value ->
            if (value is String) visit(value) else (value as List<String>).forEach(visit)
        }
    }

    class Builder {
        private val entries = linkedMapOf<String, Any>()

        /** 为名称 [name] 添加候选 [id]，去除同名重复 ID 并保留候选插入顺序。 */
        @Suppress("UNCHECKED_CAST")
        fun add(name: String, id: String) {
            when (val existing = entries[name]) {
                null -> entries[name] = id
                is String -> if (existing != id) entries[name] = arrayListOf(existing, id)
                else -> (existing as MutableList<String>).let { if (id !in it) it.add(id) }
            }
        }

        /** 使用已经收集的名称条目生成只读查询入口，结束构建阶段。 */
        fun build(): ExportNameIndex = ExportNameIndex(entries)
    }
}