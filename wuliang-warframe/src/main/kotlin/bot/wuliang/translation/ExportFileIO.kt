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
    /** 流式计算 [path] 的 Git blob SHA-1，校验文件大小并确认读取期间字节数未变化。 */
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

    /** 完整读取并校验导出文件 [file] 的大小与 Git blob 摘要，不保留文件内容。 */
    fun validate(file: ExportFile, path: Path) = read(file, path) { }

    /**
     * 校验导出文件后调用 [consume]，消费结束时继续读取剩余字节以完成完整性校验。
     *
     * @param file 固定版本清单中的文件大小与摘要。
     * @param path 本地文件路径。
     * @param consume 消费受限输入流的操作，不能关闭底层校验流。
     * @return 消费操作的返回值，只有完整性校验通过后才返回。
     */
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

    /** 创建已写入 Git blob 长度头的 SHA-1 摘要器，供后续文件内容校验使用。 */
    private fun blobDigest(size: Long): MessageDigest = MessageDigest.getInstance("SHA-1").also {
        it.update("blob $size\u0000".toByteArray(Charsets.UTF_8))
    }
}

internal class LimitedExportInputStream(input: InputStream, private val maximum: Long) : FilterInputStream(input) {
    var count = 0L
        private set

    /** 读取单个字节并更新已读取计数；到达流末尾时返回 -1。 */
    override fun read(): Int = `in`.read().also { if (it >= 0) record(1) }

    /** 按 [offset] 与 [length] 将字节读取到 [bytes]，更新计数并校验最大允许大小。 */
    override fun read(bytes: ByteArray, offset: Int, length: Int): Int =
        `in`.read(bytes, offset, length).also { if (it > 0) record(it) }

    // 不能绕过计数与摘要跳过字节。
    /** 通过有界缓冲区读取并丢弃最多 [size] 字节，使跳过操作仍参与字节计数与摘要校验。 */
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

    /** 累计读取字节数，超过输入流允许大小时拒绝继续处理。 */
    private fun record(size: Int) {
        count += size
        require(count <= maximum) { "导出文件超过允许大小" }
    }
}

internal class NonClosingExportInputStream(input: InputStream) : FilterInputStream(input) {
    /** 保留底层校验输入流的生命周期，忽略消费方的关闭请求。 */
    override fun close() = Unit
}

internal class LimitedExportOutputStream(output: OutputStream, private val maximum: Long) : FilterOutputStream(output) {
    private var count = 0L

    /** 校验累计大小后向底层流写入单个字节。 */
    override fun write(value: Int) {
        record(1)
        out.write(value)
    }

    /** 校验累计大小后将 [bytes] 中指定区间写入底层流。 */
    override fun write(bytes: ByteArray, offset: Int, length: Int) {
        record(length)
        out.write(bytes, offset, length)
    }

    /** 累计写出字节数，超过官方导出允许大小时拒绝继续写入。 */
    private fun record(size: Int) {
        count += size
        require(count <= maximum) { "官方导出超过允许大小" }
    }
}