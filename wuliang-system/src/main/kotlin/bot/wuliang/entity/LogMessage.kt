package bot.wuliang.entity

data class LogMessage(val id: Long, val level: String, val data: String)

data class ConsoleLogFrame(val type: String, val entries: List<LogMessage> = emptyList())