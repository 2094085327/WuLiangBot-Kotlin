package bot.wuliang.config

import java.util.concurrent.ConcurrentHashMap
import javax.websocket.Session

object WebSocketSessionConfig {
    // 存储每个用户的Session
    val sessionsMap: MutableMap<String, Session> = ConcurrentHashMap()

    // 存储每个用户的当前日志位置，用于增量读取新日志
    val logLengthMap: MutableMap<String, Int> = ConcurrentHashMap()

    // 添加一个用于跟踪当前文件的映射
    val currentLogFileMap = mutableMapOf<String, String>() // sessionId -> 当前文件名

}