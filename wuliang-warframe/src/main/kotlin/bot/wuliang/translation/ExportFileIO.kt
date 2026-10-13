package bot.wuliang.translation

import bot.wuliang.config.PublicExportPolicy
import java.io.FilterInputStream
import java.io.FilterOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.*

/** 文件内容按固定大小缓冲区读取；校验覆盖解析后的剩余字节，不把整个文件放入堆。 */
internal object ExportFileIO {
    fun blobSha(path: Path): String {
        val size = Files.size(path)
        require(size in 2..PublicExportPolicy.MAX_FILE_BYTES.toLong()) { "导出文件大小无效" }
        val digest = blobDigest(size)
        Files.newInputStream(path).use { raw ->
            val input = LimitedExportInputStream(DigestInputStream(raw, digest), size)
            input.transferTo(OutputStream.nullOutputStream())
            require(input.count == size) { "导出文件读取期间大小发生变化" }
        }
        return HexFormat.of().formatHex(digest.digest())
    }

    fun validate(file: ExportFile, path: Path) = read(file, path) { }

    fun <T> read(file: ExportFile, path: Path, consume: (InputStream) -> T): T {
        require(Files.size(path) == file.size) { "${file.path} 完整性校验失败" }
        val digest = blobDigest(file.size)
        return Files.newInputStream(path).use { raw ->
            val input = LimitedExportInputStream(DigestInputStream(raw, digest), file.size)
            val result = consume(NonClosingExportInputStream(input))
            input.transferTo(OutputStream.nullOutputStream())
            require(input.count == file.size && HexFormat.of().formatHex(digest.digest()) == file.sha) {
                "${file.path} 完整性校验失败"
            }
            result
        }
    }

    private fun blobDigest(size: Long): MessageDigest = MessageDigest.getInstance("SHA-1").also {
        it.update("blob $size\u0000".toByteArray(Charsets.UTF_8))
    }
}

internal class LimitedExportInputStream(input: InputStream, private val maximum: Long) : FilterInputStream(input) {
    var count = 0L
        private set

    override fun read(): Int = `in`.read().also { if (it >= 0) record(1) }

    override fun read(bytes: ByteArray, offset: Int, length: Int): Int =
        `in`.read(bytes, offset, length).also { if (it > 0) record(it) }

    // 不能绕过计数与摘要跳过字节。
    override fun skip(size: Long): Long {
        if (size <= 0) return 0
        val buffer = ByteArray(minOf(size, 8192L).toInt())
        var remaining = size
        while (remaining > 0) {
            val read = read(buffer, 0, minOf(remaining, buffer.size.toLong()).toInt())
            if (read < 0) break
            remaining -= read
        }
        return size - remaining
    }

    private fun record(size: Int) {
        count += size
        require(count <= maximum) { "导出文件超过允许大小" }
    }
}

internal class NonClosingExportInputStream(input: InputStream) : FilterInputStream(input) {
    override fun close() = Unit
}

internal class LimitedExportOutputStream(output: OutputStream, private val maximum: Long) : FilterOutputStream(output) {
    private var count = 0L

    override fun write(value: Int) {
        record(1)
        out.write(value)
    }

    override fun write(bytes: ByteArray, offset: Int, length: Int) {
        record(length)
        out.write(bytes, offset, length)
    }

    private fun record(size: Int) {
        count += size
        require(count <= maximum) { "官方导出超过允许大小" }
    }
}