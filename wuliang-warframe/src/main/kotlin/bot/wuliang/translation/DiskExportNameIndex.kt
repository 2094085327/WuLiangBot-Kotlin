package bot.wuliang.translation

import bot.wuliang.config.PublicExportPolicy
import java.io.*
import java.lang.ref.Reference
import java.nio.ByteBuffer
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.nio.file.StandardOpenOption.APPEND
import java.security.MessageDigest
import java.util.*

/** 完整名称留在磁盘；内存只保留哈希偏移表，以及最多1MiB、256项的查询缓存。 */
internal class DiskExportNameIndex private constructor(
    private val file: Path,
    private val hashes: IntArray,
    private val offsets: LongArray,
    private val dataStart: Long,
    private val dataEnd: Long,
    protect: Boolean = true,
) {
    init {
        if (protect) SnapshotUsage.retain(this, file.parent)
    }

    val size: Int get() = hashes.size
    private val cache = LinkedHashMap<String, List<String>>(16, 0.75f, true)
    private var cacheBytes = 0L

    /** 二分定位同哈希段，逐条核对完整名称 */
    operator fun get(name: String): List<String> = try {
        lookup(name)
    } finally {
        Reference.reachabilityFence(this)
    }

    private fun lookup(name: String): List<String> {
        synchronized(cache) { cache[name]?.let { return it } }
        val hash = name.hashCode()
        var position = Arrays.binarySearch(hashes, hash)
        val result = if (position < 0) emptyList() else RandomAccessFile(file.toFile(), "r").use { input ->
            val records = Records(input, dataEnd) { input.filePointer }
            while (position > 0 && hashes[position - 1] == hash) position--
            while (position < size && hashes[position] == hash) {
                input.seek(offsets[position++])
                if (records.text() == name) return@use records.ids()
            }
            emptyList()
        }
        remember(name, result)
        return result
    }

    /** 按原始插入顺序遍历候选，保持中文索引和同名候选的既有顺序。 */
    fun forEachId(visit: (String) -> Unit) {
        try {
            visitIds(visit)
        } finally {
            Reference.reachabilityFence(this)
        }
    }

    private fun visitIds(visit: (String) -> Unit) {
        val counted = LimitedExportInputStream(BufferedInputStream(Files.newInputStream(file), 64 * 1024), dataEnd)
        DataInputStream(counted).use { input ->
            input.skipNBytes(dataStart)
            val records = Records(input, dataEnd) { counted.count }
            repeat(size) {
                records.skipText()
                records.ids().forEach(visit)
            }
            require(counted.count == dataEnd) { "名称索引记录长度无效" }
        }
    }

    private fun remember(name: String, result: List<String>) {
        val entryBytes = weight(name, result)
        if (entryBytes > CACHE_BYTES) return
        synchronized(cache) {
            cache.put(name, result)?.let { cacheBytes -= weight(name, it) }
            cacheBytes += entryBytes
            val iterator = cache.entries.iterator()
            while (cacheBytes > CACHE_BYTES || cache.size > CACHE_ENTRIES) {
                val entry = iterator.next()
                cacheBytes -= weight(entry.key, entry.value)
                iterator.remove()
            }
        }
    }

    companion object {
        private const val MAGIC = 0x57464e49
        private const val DIGEST_BYTES = 32
        private const val OFFSET_BYTES = 12L
        private const val CACHE_ENTRIES = 256
        private const val CACHE_BYTES = 1024L * 1024

        /** 校验完整文件摘要、来源指纹及偏移边界 */
        fun open(file: Path, fingerprint: String, protect: Boolean = true): DiskExportNameIndex? {
            if (!Files.exists(file)) return null
            RandomAccessFile(file.toFile(), "r").use { input ->
                val dataEnd = input.length() - DIGEST_BYTES
                require(dataEnd >= 12) { "名称索引文件不完整" }
                input.seek(dataEnd)
                val expected = ByteArray(DIGEST_BYTES).also(input::readFully)
                require(MessageDigest.isEqual(digest(file, dataEnd), expected)) { "名称索引摘要不匹配" }
                input.seek(0)
                require(
                    input.readInt() == MAGIC && Records(
                        input,
                        dataEnd
                    ) { input.filePointer }.text() == fingerprint
                ) {
                    "名称索引来源不匹配"
                }
                val count = input.readInt()
                val dataStart = input.filePointer + count.toLong() * OFFSET_BYTES
                require(count >= 0 && dataStart in input.filePointer..dataEnd) { "名称索引数量无效" }
                val hashes = IntArray(count)
                val offsets = LongArray(count)
                val bytes = ByteArray(8192 / OFFSET_BYTES.toInt() * OFFSET_BYTES.toInt())
                var position = 0
                while (position < count) {
                    val entries = minOf(count - position, bytes.size / OFFSET_BYTES.toInt())
                    input.readFully(bytes, 0, entries * OFFSET_BYTES.toInt())
                    val buffer = ByteBuffer.wrap(bytes)
                    repeat(entries) {
                        hashes[position] = buffer.int
                        offsets[position] = buffer.long
                        require(
                            offsets[position] in dataStart until dataEnd &&
                                    (position == 0 || hashes[position - 1] <= hashes[position])
                        ) { "名称索引偏移无效" }
                        position++
                    }
                }
                return DiskExportNameIndex(file, hashes, offsets, dataStart, dataEnd, protect)
            }
        }

        /** 临时文件完全写入并校验后发布 */
        fun write(file: Path, fingerprint: String, entries: Map<String, Any>): DiskExportNameIndex {
            val temporary = file.resolveSibling("${file.fileName}-${UUID.randomUUID()}.tmp")
            try {
                Files.createDirectories(file.parent)
                val sorted = LongArray(entries.size)
                val offsets = LongArray(entries.size)
                val indexStart = 12L + fingerprint.toByteArray(Charsets.UTF_8).size
                val dataStart = indexStart + entries.size.toLong() * OFFSET_BYTES
                DataOutputStream(BufferedOutputStream(Files.newOutputStream(temporary), 64 * 1024)).use { output ->
                    output.writeInt(MAGIC)
                    writeText(output, fingerprint)
                    output.writeInt(entries.size)
                    val padding = ByteArray(8192)
                    var remaining = entries.size.toLong() * OFFSET_BYTES
                    while (remaining > 0) {
                        val length = minOf(remaining, padding.size.toLong()).toInt()
                        output.write(padding, 0, length)
                        remaining -= length
                    }
                    var positionInFile = dataStart
                    entries.entries.forEachIndexed { position, (name, value) ->
                        sorted[position] = (name.hashCode().toLong() shl 32) or position.toLong()
                        offsets[position] = positionInFile
                        positionInFile += writeText(output, name)
                        val ids = ids(value)
                        output.writeInt(ids.size)
                        positionInFile += 4
                        ids.forEach { positionInFile += writeText(output, it) }
                    }
                }
                Arrays.sort(sorted)
                RandomAccessFile(temporary.toFile(), "rw").use { output ->
                    output.seek(indexStart)
                    val buffer = ByteBuffer.allocate(8192 / OFFSET_BYTES.toInt() * OFFSET_BYTES.toInt())
                    sorted.forEach { entry ->
                        if (buffer.remaining() < OFFSET_BYTES) {
                            output.write(buffer.array(), 0, buffer.position())
                            buffer.clear()
                        }
                        buffer.putInt((entry shr 32).toInt())
                        buffer.putLong(offsets[entry.toInt()])
                    }
                    output.write(buffer.array(), 0, buffer.position())
                }
                Files.newOutputStream(temporary, APPEND).use { it.write(digest(temporary, Files.size(temporary))) }
                val verified = requireNotNull(open(temporary, fingerprint, protect = false))
                Files.move(temporary, file, ATOMIC_MOVE, REPLACE_EXISTING)
                return DiskExportNameIndex(
                    file,
                    verified.hashes,
                    verified.offsets,
                    verified.dataStart,
                    verified.dataEnd
                )
            } finally {
                Files.deleteIfExists(temporary)
            }
        }

        @Suppress("UNCHECKED_CAST")
        private fun ids(value: Any): List<String> = if (value is String) listOf(value) else value as List<String>

        /** 按字符串内容估算缓存权重 */
        private fun weight(name: String, ids: List<String>): Long =
            128L + name.length.toLong() * 2 + ids.sumOf { 48L + it.length.toLong() * 2 }

        private fun writeText(output: DataOutput, value: String): Long {
            val bytes = value.toByteArray(Charsets.UTF_8)
            output.writeInt(bytes.size)
            output.write(bytes)
            return 4L + bytes.size
        }

        private fun digest(file: Path, length: Long): ByteArray {
            val digest = MessageDigest.getInstance("SHA-256")
            Files.newInputStream(file).use { input ->
                val buffer = ByteArray(8192)
                var remaining = length
                while (remaining > 0) {
                    val read = input.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                    require(read > 0) { "名称索引文件不完整" }
                    digest.update(buffer, 0, read)
                    remaining -= read
                }
            }
            return digest.digest()
        }
    }

    /** 同时支持随机定位和有缓冲的顺序遍历，所有长度均受文件边界约束。 */
    private class Records(private val input: DataInput, private val end: Long, private val position: () -> Long) {
        private fun length(): Int {
            require(position() + 4 <= end) { "名称索引文本不完整" }
            return input.readInt().also {
                require(it in 0..PublicExportPolicy.MAX_FILE_BYTES && position() + it <= end) { "名称索引文本长度无效" }
            }
        }

        fun text(): String = ByteArray(length()).also(input::readFully).toString(Charsets.UTF_8)

        fun skipText() {
            var remaining = length()
            while (remaining > 0) {
                val skipped = input.skipBytes(remaining)
                require(skipped > 0) { "名称索引文本不完整" }
                remaining -= skipped
            }
        }

        fun ids(): List<String> {
            require(position() + 4 <= end) { "名称索引候选不完整" }
            val count = input.readInt()
            require(count > 0 && count.toLong() * 4 <= end - position()) { "名称索引候选数量无效" }
            return List(count) { text() }
        }
    }
}