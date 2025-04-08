package bot.wuliang.botLog.logUtil

import org.slf4j.Logger
import org.slf4j.LoggerFactory

@Suppress("unused")
object LoggerUtils {
    fun  Any.logInfo(msg : String, vararg params: Any) {
        Log.log(this.javaClass, Log.LogLevel.INFO, msg, params)
    }
    fun  Any.logTrace(msg : String, vararg params: Any) {
        Log.log(this.javaClass, Log.LogLevel.TRACE, msg, params)
    }
    fun  Any.logError(msg : String, vararg params: Any) {
        Log.log(this.javaClass, Log.LogLevel.ERROR, msg, params)
    }
    fun  Any.logDebug(msg : String, vararg params: Any) {
        Log.log(this.javaClass, Log.LogLevel.DEBUG, msg, params)
    }
    fun  Any.logWarn(msg : String, vararg params: Any) {
        Log.log(this.javaClass, Log.LogLevel.WARN, msg, params)
    }

    object Log{
        private val logs : MutableMap<Class<Any>, Logger> = HashMap()


        fun log(clazz: Class<Any>, level: LogLevel, msg: String, params: Array<out Any>) {
            synchronized(logs) { // 使用同步块保护对 logs 的访问
                val logger = logs.computeIfAbsent(clazz) {
                    LoggerFactory.getLogger(it)
                }
                when (level) {
                    LogLevel.INFO -> logger.info(msg, *params)
                    LogLevel.TRACE -> logger.trace(msg, *params)
                    LogLevel.DEBUG -> logger.debug(msg, *params)
                    LogLevel.WARN -> logger.warn(msg, *params)
                    LogLevel.ERROR -> logger.error(msg, *params)
                }
            }
        }

        enum class LogLevel{
            TRACE,
            DEBUG,
            INFO,
            WARN,
            ERROR
        }
    }

}