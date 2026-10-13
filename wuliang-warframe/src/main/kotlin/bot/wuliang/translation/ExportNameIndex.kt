package bot.wuliang.translation

/** 单候选直接保存 ID；只有同名多候选时才分配列表，保留候选插入顺序。 */
internal class ExportNameIndex private constructor(private val entries: Map<String, Any>) {
    @Suppress("UNCHECKED_CAST")
    operator fun get(name: String): List<String> = when (val value = entries[name]) {
        null -> emptyList()
        is String -> listOf(value)
        else -> value as List<String>
    }

    @Suppress("UNCHECKED_CAST")
    fun forEachId(visit: (String) -> Unit) {
        entries.values.forEach { value ->
            if (value is String) visit(value) else (value as List<String>).forEach(visit)
        }
    }

    class Builder {
        private val entries = linkedMapOf<String, Any>()

        @Suppress("UNCHECKED_CAST")
        fun add(name: String, id: String) {
            when (val existing = entries[name]) {
                null -> entries[name] = id
                is String -> if (existing != id) entries[name] = arrayListOf(existing, id)
                else -> (existing as MutableList<String>).let { if (id !in it) it.add(id) }
            }
        }

        fun build(): ExportNameIndex = ExportNameIndex(entries)
    }
}