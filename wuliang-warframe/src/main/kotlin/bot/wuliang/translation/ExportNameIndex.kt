package bot.wuliang.translation

import java.nio.file.Path

/** 单候选直接保存 ID；只有同名多候选时才分配列表，保留候选插入顺序。 */
internal class ExportNameIndex private constructor(
    private val entries: Map<String, Any>?,
    private val disk: DiskExportNameIndex? = null,
) {
    val size: Int get() = disk?.size ?: entries!!.size

    /** 按规范名称读取候选 ID，保持插入顺序；没有候选时返回空列表。 */
    @Suppress("UNCHECKED_CAST")
    operator fun get(name: String): List<String> = disk?.get(name) ?: when (val value = entries!![name]) {
        null -> emptyList()
        is String -> listOf(value)
        else -> value as List<String>
    }

    /** 对名称索引中的每个候选 ID 执行 [visit]，兼容单候选与多候选的内部存储形式。 */
    @Suppress("UNCHECKED_CAST")
    fun forEachId(visit: (String) -> Unit) {
        if (disk != null) return disk.forEachId(visit)
        entries!!.values.forEach { value ->
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

        /** 完整名称及候选写入磁盘，返回只持有偏移表和有界热缓存的索引。 */
        internal fun build(file: Path, fingerprint: String): ExportNameIndex =
            ExportNameIndex(null, DiskExportNameIndex.write(file, fingerprint, entries))
    }

    companion object {
        /** 复用完整性校验通过的派生索引；首次加载或文件损坏时重建该索引。 */
        fun onDisk(file: Path, fingerprint: String, build: () -> Builder): ExportNameIndex {
            val existing = recoverExportData { DiskExportNameIndex.open(file, fingerprint) }.getOrNull()
            return if (existing != null) ExportNameIndex(null, existing) else build().build(file, fingerprint)
        }
    }
}