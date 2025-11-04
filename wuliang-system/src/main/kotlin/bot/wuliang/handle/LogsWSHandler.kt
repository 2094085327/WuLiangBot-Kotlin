package bot.wuliang.handle

import bot.wuliang.botLog.logUtil.LoggerUtils.logInfo
import bot.wuliang.config.WebSocketSessionConfig.currentLogFileMap
import bot.wuliang.config.WebSocketSessionConfig.logLengthMap
import bot.wuliang.config.WebSocketSessionConfig.sessionsMap
import bot.wuliang.entity.LogMessage
import bot.wuliang.config.WsMessageEncoder
import org.springframework.stereotype.Component
import org.springframework.beans.factory.annotation.Value
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.text.SimpleDateFormat
import java.util.*
import javax.websocket.*
import javax.websocket.server.ServerEndpoint
import kotlin.math.min

@Component
@ServerEndpoint("/logs", encoders = [WsMessageEncoder::class])
class LogsWSHandler {

    companion object {
        private var logFileName: String = ""
    }

    @Value("\${logging.logfile.name}")
    fun getLogFileName(fileName: String) {
        logFileName = fileName
    }
    /**
     * 查找最新的日志切片文件
     */
    private fun findLatestLogFile(logDir: File, appName: String): File? {
        if (!logDir.exists() || !logDir.isDirectory) {
            return null
        }

        val logFiles = logDir.listFiles { file ->
            file.name.startsWith("$appName.part_") && file.name.endsWith(".log")
        }

        return logFiles?.maxByOrNull { file ->
            // 提取切片序号进行比较
            val regex = Regex("""part_(\d+)\.log""")
            val matchResult = regex.find(file.name)
            matchResult?.groupValues?.get(1)?.toIntOrNull() ?: 0
        }
    }


    /**
     * 建立连接成功调用
     */
    @OnOpen
    fun onOpen(session: Session) {
        sessionsMap[session.id] = session
        logLengthMap[session.id] = 0
        currentLogFileMap[session.id] = ""
        Thread {
            logInfo("Session:${session.id} -开始监听日志文件")
            var first = true
            while (sessionsMap[session.id] != null) {
                var reader: BufferedReader? = null
                try {
                    // 获取最新的日志切片文件
                    val logDirPath = "logs/" + SimpleDateFormat("yyyyMMdd").format(Date())
                    val logDir = File(logDirPath)

                    // 查找最新的切片文件
                    val latestFile = findLatestLogFile(logDir, logFileName)
                    if (latestFile == null) {
                        Thread.sleep(1000)
                        continue
                    }

                    // 检查是否切换了日志文件
                    val latestFileName = latestFile.name
                    val currentFileName = currentLogFileMap[session.id]
                    var needReset = false

                    if (currentFileName != latestFileName) {
                        // 文件已切换，需要重置计数
                        needReset = true
                        currentLogFileMap[session.id] = latestFileName
                    }

                    // 字符流
                    reader = BufferedReader(FileReader(latestFile))
                    val lines = reader.lines().toArray()

                    // 只取从上次之后产生的日志
                    val startIndex = if (needReset) {
                        0 // 新文件从头开始读取
                    } else {
                        min(logLengthMap[session.id] ?: 0, lines.size)
                    }
                    var copyOfRange = Arrays.copyOfRange(lines, startIndex, lines.size)

                    // 存储最新一行开始
                    logLengthMap[session.id] = lines.size

                    // 第一次如果太大，截取最新的200行就够了，避免传输的数据太大
                    if (first && copyOfRange.size > 200) {
                        val newStartIndex = copyOfRange.size - 200
                        val endIndex = copyOfRange.size
                        val newCopyOfRange = Arrays.copyOfRange(copyOfRange, newStartIndex, endIndex)
                        copyOfRange = newCopyOfRange
                        first = false
                    }

                    // 将日志行转换为Message对象数组
                    val messages = copyOfRange.map { line ->
                        val logLine = line as String
                        // 提取日志等级
                        val level = when {
                            logLine.contains("DEBUG") -> "DEBUG"
                            logLine.contains("INFO") -> "INFO"
                            logLine.contains("WARN") -> "WARNING"
                            logLine.contains("ERROR") -> "ERROR"
                            else -> "INFO"
                        }

                        val coloredLine = when (level) {
                            "DEBUG" -> logLine.replace("DEBUG", "\u001b[34mDEBUG\u001b[0m")
                            "INFO" -> logLine.replace("INFO", "\u001b[1;32mINFO\u001b[0m")
                            "WARNING" -> logLine.replace("WARN", "\u001b[1;33mWARNING\u001b[0m")
                            "ERROR" -> logLine.replace("ERROR", "\u001b[1;31mERROR\u001b[0m")
                            else -> logLine
                        }.let { processedLine ->
                            // 添加额外的颜色处理
                            var result = processedLine

                            // 匹配日期时间部分到 --- 之前的部分（紫色）
                            val purpleRegex = Regex("""(\u001b\[0m\s*)(.*?)(?=\s+---)""")
                            result = result.replace(purpleRegex) { match ->
                                "${match.groupValues[1]}\u001b[35m${match.groupValues[2]}\u001b[0m"
                            }

                            // 匹配 ] 到 : 之间的部分（青色）
                            val cyanRegex = Regex("""](.*?)(?=: )""")
                            result = result.replace(cyanRegex) { match ->
                                "]\u001b[36m${match.groupValues[1]}\u001b[0m"
                            }

                            result
                        }

                        LogMessage(level = level, data = coloredLine)
                    }.toList()

                    // 发送Message数组
                    if (messages.isNotEmpty()) {
                        sendObject(session, messages)
                    }

                    // 休眠一秒
                    Thread.sleep(1000)
                } catch (e: Exception) {
                    // 显式处理异常
                    e.printStackTrace()
                } finally {
                    reader?.close()
                }
            }

        }.start()
    }

    /**
     * 关闭连接调用
     */
    @OnClose
    fun onClose(session: Session) {
        logInfo("Session:${session.id} -关闭连接")
        sessionsMap.remove(session.id)
        logLengthMap.remove(session.id)
    }

    fun sendObject(session: Session, message: Any) {
        session.basicRemote.sendObject(message)
    }
}