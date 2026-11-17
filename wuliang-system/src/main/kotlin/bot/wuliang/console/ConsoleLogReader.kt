package bot.wuliang.console

import bot.wuliang.console.ConsoleLogReader.Companion.HISTORY_BYTES
import bot.wuliang.console.ConsoleLogReader.Companion.HISTORY_LINES
import bot.wuliang.console.ConsoleLogReader.Companion.MAX_READ_BYTES
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardOpenOption.READ
import java.nio.file.attribute.BasicFileAttributes
import java.time.Clock
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*

/**
 * 按字节位置增量读取滚动日志，并限制单轮读取量和单行大小。
 *
 * 首次读取近期历史，后续按日期和切片编号继续读取。未结束的行保留到下一轮。
 * 本类保存可变读取状态，调用方须串行调用 [read]。文件通道在每轮读取后关闭。
 *
 * @param root 日志根目录，其日期子目录使用 yyyyMMdd 格式。
 * @param name 日志文件名前缀，用于匹配名称为“前缀.part_序号.log”的切片。
 * @param clock 用于确定当天日期的时钟，默认使用系统时区。
 */
class ConsoleLogReader(
    private val root: Path,
    name: String,
    private val clock: Clock = Clock.systemDefaultZone(),
) {
    /**
     * 一行已解码的日志或控制台提示。
     *
     * @property level 规范化后的日志级别，异常续行沿用前一日志头的级别。
     * @property data 不含行尾换行符的日志文本，超长行包含截断提示。
     */
    data class Line(val level: String, val data: String) {
        /** [data] 按 UTF-8 编码后的字节数，用于统计历史记录容量。 */
        val bytes: Int get() = data.toByteArray(Charsets.UTF_8).size
    }

    /**
     * 一轮读取的输出。
     *
     * @property lines 按读取顺序排列的日志行和控制台提示。
     * @property reset 是否要求调用方清空旧历史，并以本轮结果重建快照。
     */
    data class Result(val lines: List<Line>, val reset: Boolean = false)

    /**
     * 日志切片的位置和排序信息。
     *
     * @property path 包含日期目录的切片路径。
     * @property date 切片所属的目录日期。
     * @property part 从文件名解析出的切片序号。
     */
    private data class LogFile(val path: Path, val date: LocalDate, val part: Long)

    private val filePattern = Regex("^${Regex.escape(name)}\\.part_(\\d+)\\.log$")
    private val buffer = ByteBuffer.allocate(8192)
    private val pending = ByteArrayOutputStream(MAX_LINE_BYTES)
    private var current: LogFile? = null
    private var identity: Any? = null
    private var offset = 0L
    private var skippingPrefix = false
    private var truncated = false
    private var lastLevel = "INFO"

    /**
     * 读取近期历史或本轮新增日志，并在失败时恢复读取前的状态。
     *
     * 文件位置、未完成的行和继承的日志级别一并回退，避免重试时跳过日志。
     *
     * @return 本轮日志及历史重置标记；没有可输出内容时，日志列表为空。
     * @throws Exception 读取或处理失败时原样抛出，读取状态已恢复。
     */
    fun read(): Result {
        val savedFile = current
        val savedIdentity = identity
        val savedOffset = offset
        val savedPending = pending.toByteArray()
        val savedSkipping = skippingPrefix
        val savedTruncated = truncated
        val savedLevel = lastLevel
        return try {
            readAvailable()
        } catch (exception: Exception) {
            // 本轮尚未交付时不提交读取位置，避免重试跳过已解码的行。
            current = savedFile
            identity = savedIdentity
            offset = savedOffset
            pending.reset()
            pending.write(savedPending)
            skippingPrefix = savedSkipping
            truncated = savedTruncated
            lastLevel = savedLevel
            throw exception
        }
    }

    /**
     * 在单轮预算内读取当前文件，并按顺序切换后续切片。
     *
     * 文件替换或截断时从头读取。当前文件不可用时重新加载近期历史。
     * 切片切换前先输出旧文件尚未结束的行，缺少中间切片时附加提示。
     *
     * @return 首次历史或本轮新增日志；重新加载历史时，[Result.reset] 为 true。
     * @throws java.io.IOException 遍历目录、打开文件或读取内容失败时抛出，由 [read] 回退状态。
     */
    private fun readAvailable(): Result {
        if (current == null) return Result(loadRecent())
        val lines = ArrayList<Line>()
        var budget = MAX_READ_BYTES
        var switches = 0
        while (budget > 0 && lines.size < MAX_READ_LINES && switches < 16) {
            val file = current ?: break
            val attrs = attributes(file.path)
            if (attrs == null) {
                current = null
                val recent = loadRecent()
                return Result(listOf(notice("日志文件已变化，重新载入近期日志")) + recent, reset = true)
            }
            val key = attrs.fileKey() ?: attrs.creationTime()
            if (key != identity || attrs.size() < offset) {
                select(file, attrs, 0)
                lines.add(notice("日志文件已重建，从新内容继续读取"))
            }
            FileChannel.open(file.path, READ).use { channel ->
                budget -= drain(channel, budget, { lines.size >= MAX_READ_LINES }) { lines.add(it) }
            }
            if (offset < Files.size(file.path) || lines.size >= MAX_READ_LINES) break
            val next = findNext(file) ?: break
            if (pending.size() > 0 || truncated) completeLine()?.let(lines::add)
            if (next.date == file.date && next.part > file.part + 1) {
                lines.add(notice("部分日志切片已不存在，继续读取可用内容"))
            }
            val nextAttrs = attributes(next.path) ?: break
            select(next, nextAttrs, 0, keepLevel = true)
            switches++
        }
        return Result(lines)
    }

    /**
     * 从最新日期的最大编号切片中加载有界的近期历史，并设置后续读取位置。
     *
     * 最多读取文件尾部 [MAX_READ_BYTES] 字节。从文件中部开始时，舍弃首个行片段。
     * 只保留满足 [HISTORY_LINES] 和 [HISTORY_BYTES] 限制的最近日志，未结束的行留待后续拼接。
     *
     * @return 按原顺序排列的近期日志；未找到切片或无法取得其属性时返回空列表。
     * @throws java.io.IOException 遍历目录、打开切片或读取内容失败时抛出。
     */
    private fun loadRecent(): List<Line> {
        val file = dates().asReversed().firstNotNullOfOrNull { date -> files(date).lastOrNull() } ?: return emptyList()
        val attrs = attributes(file.path) ?: return emptyList()
        val start = (attrs.size() - MAX_READ_BYTES).coerceAtLeast(0)
        select(file, attrs, start)
        skippingPrefix = start > 0
        val recent = ArrayDeque<Line>()
        var bytes = 0
        FileChannel.open(file.path, READ).use { channel ->
            drain(channel, MAX_READ_BYTES, { false }) { line ->
                recent.addLast(line)
                bytes += line.bytes
                while (recent.size > HISTORY_LINES || bytes > HISTORY_BYTES) bytes -= recent.removeFirst().bytes
            }
        }
        return recent.toList()
    }

    /**
     * 切换当前文件并设置字节位置，清空上一文件的未完成行和截断标记。
     *
     * @param file 要读取的日志切片。
     * @param attrs 当前文件属性，用文件标识或创建时间识别文件替换。
     * @param start 读取起点，单位为字节。
     * @param keepLevel 是否保留前一日志级别；连续切片切换时保留，以支持跨片堆栈。
     */
    private fun select(file: LogFile, attrs: BasicFileAttributes, start: Long, keepLevel: Boolean = false) {
        current = file
        identity = attrs.fileKey() ?: attrs.creationTime()
        offset = start
        pending.reset()
        truncated = false
        skippingPrefix = false
        if (!keepLevel) lastLevel = "INFO"
    }

    /**
     * 从当前字节位置读取内容，按换行符交付日志，并保留未结束的行。
     *
     * 只有已处理的字节才推进位置。停止条件触发后，缓冲区中未处理的内容留待下轮重读。
     *
     * @param channel 待读取的文件通道，由调用方创建和关闭。
     * @param budget 本次最多从通道读入的字节数。
     * @param stop 返回 true 时停止读取或处理缓冲区；该回调会被重复调用，应无副作用。
     * @param consume 接收每条已完成日志行的回调，不接收被舍弃的首个行片段。
     * @return 实际从通道读入缓冲区的字节数，可能大于本次已处理的字节数。
     * @throws java.io.IOException 定位或读取通道失败时抛出。
     */
    private fun drain(channel: FileChannel, budget: Int, stop: () -> Boolean, consume: (Line) -> Unit): Int {
        channel.position(offset)
        var read = 0
        while (read < budget && !stop()) {
            buffer.clear()
            buffer.limit(minOf(buffer.capacity(), budget - read))
            val count = channel.read(buffer)
            if (count <= 0) break
            read += count
            buffer.flip()
            while (buffer.hasRemaining() && !stop()) {
                val byte = buffer.get().toInt() and 0xff
                offset++
                when {
                    byte == 10 -> completeLine()?.let(consume)
                    skippingPrefix -> Unit
                    pending.size() < MAX_LINE_BYTES -> pending.write(byte)
                    else -> truncated = true
                }
            }
        }
        return read
    }

    /**
     * 用 UTF-8 解码当前行，移除末尾回车，并清空行缓冲和截断标记。
     *
     * 超长行附加截断提示。有效日志头更新级别，异常堆栈等续行沿用前一日志级别。
     *
     * @return 已处理的日志行；从文件中部读取时，首个待舍弃片段返回 null。
     */
    private fun completeLine(): Line? {
        if (skippingPrefix) {
            skippingPrefix = false
            pending.reset()
            return null
        }
        val raw = pending.toByteArray()
        val length = if (raw.lastOrNull() == 13.toByte()) raw.size - 1 else raw.size
        val text = String(raw, 0, length, Charsets.UTF_8) + if (truncated) "…（单行过长，已截断）" else ""
        pending.reset()
        truncated = false
        HEADER.find(text)?.groupValues?.get(1)?.let { level ->
            lastLevel = when (level) {
                "TRACE" -> "DEBUG"; "WARN" -> "WARNING"; "FATAL" -> "ERROR"; else -> level
            }
        }
        return Line(lastLevel, text)
    }

    /**
     * 按日期和切片序号查找当前文件之后的首个可用切片。
     *
     * @param file 当前读取的日志切片。
     * @return 同日更大序号的首个切片，或后续日期的首个切片；没有后续切片时返回 null。
     * @throws java.io.IOException 遍历日期目录或切片目录失败时抛出。
     */
    private fun findNext(file: LogFile): LogFile? {
        for (date in dates().filter { it >= file.date }) {
            files(date).firstOrNull { date > file.date || it.part > file.part }?.let { return it }
        }
        return null
    }

    /**
     * 查找日志根目录下不晚于今天的有效日期目录，忽略非法日期和符号链接。
     *
     * @return 按日期升序排列的结果；根目录不存在、不是目录或本身为符号链接时为空。
     * @throws java.io.IOException 打开或遍历根目录失败时抛出。
     */
    private fun dates(): List<LocalDate> {
        if (!Files.isDirectory(root, NOFOLLOW_LINKS)) return emptyList()
        val today = LocalDate.now(clock)
        return Files.newDirectoryStream(root).use { paths ->
            paths.filter { Files.isDirectory(it, NOFOLLOW_LINKS) && it.fileName.toString().matches(DATE_NAME) }
                .mapNotNull {
                    runCatching {
                        LocalDate.parse(
                            it.fileName.toString(),
                            DateTimeFormatter.BASIC_ISO_DATE
                        )
                    }.getOrNull()
                }
                .filter { it <= today }.sorted()
        }
    }

    /**
     * 查找指定日期目录中符合文件名规则的普通日志切片，忽略符号链接和无效序号。
     *
     * @param date 日志切片所属的日期。
     * @return 按序号升序排列的切片；日期目录不存在或没有匹配文件时为空。
     * @throws java.io.IOException 打开或遍历日期目录失败时抛出。
     */
    private fun files(date: LocalDate): List<LogFile> {
        val directory = root.resolve(date.format(DateTimeFormatter.BASIC_ISO_DATE))
        if (!Files.isDirectory(directory, NOFOLLOW_LINKS)) return emptyList()
        return Files.newDirectoryStream(directory).use { paths ->
            paths.mapNotNull { path ->
                val part = filePattern.matchEntire(path.fileName.toString())?.groupValues?.get(1)?.toLongOrNull()
                if (part != null && Files.isRegularFile(path, NOFOLLOW_LINKS)) LogFile(path, date, part) else null
            }.sortedBy { it.part }
        }
    }

    /**
     * 读取普通文件的基本属性，不跟随符号链接。
     *
     * @param path 待检查的文件路径。
     * @return 普通文件属性；路径不存在、不是普通文件或读取属性失败时返回 null。
     */
    private fun attributes(path: Path): BasicFileAttributes? =
        runCatching {
            Files.readAttributes(path, BasicFileAttributes::class.java, NOFOLLOW_LINKS).takeIf { it.isRegularFile }
        }.getOrNull()

    /**
     * 将文件变化或缺片等情况转换为控制台提示，不写回原始日志文件。
     *
     * @param text 提示正文。
     * @return 带“控制台：”前缀的 WARNING 日志行。
     */
    private fun notice(text: String) = Line("WARNING", "控制台：$text")

    companion object {
        const val HISTORY_LINES = 200
        const val HISTORY_BYTES = 64 * 1024
        private const val MAX_READ_BYTES = 256 * 1024
        private const val MAX_READ_LINES = 1000
        private const val MAX_LINE_BYTES = 16 * 1024
        private val DATE_NAME = Regex("\\d{8}")
        private val HEADER =
            Regex("^\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}(?:[.,]\\d+)?\\s+(TRACE|DEBUG|INFO|WARN|ERROR|FATAL)\\s")
    }
}