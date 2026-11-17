package bot.wuliang.console

import bot.wuliang.config.WebSocketSessionConfig
import bot.wuliang.entity.ConsoleLogFrame
import bot.wuliang.entity.LogMessage
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.nio.file.Path
import java.util.*
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit
import javax.annotation.PreDestroy

@Component
class ConsoleLogSource(
    private val sessions: WebSocketSessionConfig,
    @Value("\${logging.logfile.root-path}") private val rootPath: String,
    @Value("\${logging.logfile.name}") private val logName: String,
) {
    private data class Entry(val message: LogMessage, val bytes: Int)

    private val lock = Any()
    private val subscribers = LinkedHashMap<String, Boolean>()
    private val history = ArrayDeque<Entry>()
    private val scheduler = ScheduledThreadPoolExecutor(1) { task ->
        Thread(task, "console-log-reader").apply { isDaemon = true }
    }.apply { removeOnCancelPolicy = true }
    private var task: ScheduledFuture<*>? = null
    private var reader: ConsoleLogReader? = null
    private var historyBytes = 0
    private var sequence = 0L
    private var failed = false
    private var stopped = false

    fun subscribe(id: String) = synchronized(lock) {
        if (stopped || !sessions.contains(id)) return@synchronized
        subscribers[id] = true
        if (task == null) {
            reader = ConsoleLogReader(Path.of(rootPath), logName)
            history.clear()
            historyBytes = 0
            failed = false
            task = scheduler.scheduleWithFixedDelay({ tick() }, 0, 1, TimeUnit.SECONDS)
        }
    }

    fun unsubscribe(id: String) = synchronized(lock) {
        subscribers.remove(id)
        if (subscribers.isEmpty()) {
            task?.cancel(false)
            task = null
            reader = null
            history.clear()
            historyBytes = 0
        }
    }

    private fun tick() = synchronized(lock) {
        if (stopped || subscribers.isEmpty()) return@synchronized
        try {
            val result = reader?.read() ?: return@synchronized
            if (result.reset) {
                history.clear()
                historyBytes = 0
                subscribers.keys.toList().forEach { subscribers[it] = true }
            }
            val lines = if (failed) listOf(
                ConsoleLogReader.Line(
                    "INFO",
                    "控制台：日志读取已恢复"
                )
            ) + result.lines else result.lines
            failed = false
            publish(lines)
        } catch (_: Exception) {
            if (!failed) publish(listOf(ConsoleLogReader.Line("WARNING", "控制台：暂时无法读取日志，正在重试")))
            failed = true
        }
    }

    private fun publish(lines: List<ConsoleLogReader.Line>) {
        val fresh = lines.map { line ->
            Entry(LogMessage(++sequence, line.level, line.data), line.bytes).also { entry ->
                history.addLast(entry)
                historyBytes += entry.bytes
                while (history.size > ConsoleLogReader.HISTORY_LINES || historyBytes > ConsoleLogReader.HISTORY_BYTES) {
                    historyBytes -= history.removeFirst().bytes
                }
            }
        }
        val snapshotIds = subscribers.filterValues { it }.keys.toList()
        val appendIds = subscribers.filterValues { !it }.keys.toList()
        if (snapshotIds.isNotEmpty()) {
            sessions.sendAll(snapshotIds, ConsoleLogFrame("snapshot", history.map { it.message }))
            snapshotIds.forEach { if (subscribers.containsKey(it)) subscribers[it] = false }
        }
        var batch = ArrayList<LogMessage>()
        var bytes = 0
        for (entry in fresh) {
            if (batch.isNotEmpty() && (batch.size >= 64 || bytes + entry.bytes > 32 * 1024)) {
                sessions.sendAll(appendIds, ConsoleLogFrame("append", batch))
                batch = ArrayList()
                bytes = 0
            }
            batch.add(entry.message)
            bytes += entry.bytes
        }
        if (batch.isNotEmpty()) sessions.sendAll(appendIds, ConsoleLogFrame("append", batch))
    }

    @PreDestroy
    fun stop() {
        synchronized(lock) {
            stopped = true
            task?.cancel(false)
            subscribers.clear()
            history.clear()
            reader = null
        }
        scheduler.shutdownNow()
    }
}