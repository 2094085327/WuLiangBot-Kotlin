package bot.wuliang.botLog.logUtil

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.slf4j.spi.LocationAwareLogger
import org.springframework.util.ClassUtils

/** 统一日志入口；支持空值占位参数，末尾的 Throwable 按 SLF4J 约定记录完整堆栈。 */
@Suppress("unused")
object LoggerUtils {
    private val callerFqcn = LoggerUtils::class.java.name

    // 按业务类缓存，不对日志输出加全局锁；类卸载时相应缓存也可回收。
    private val loggers = object : ClassValue<Logger>() {
        override fun computeValue(type: Class<*>): Logger = LoggerFactory.getLogger(type)
    }

    fun Any.logInfo(msg: String, vararg params: Any?) {
        write(javaClass, Log.LogLevel.INFO, msg, params, callerFqcn)
    }

    fun Any.logTrace(msg: String, vararg params: Any?) {
        write(javaClass, Log.LogLevel.TRACE, msg, params, callerFqcn)
    }

    fun Any.logError(msg: String, vararg params: Any?) {
        write(javaClass, Log.LogLevel.ERROR, msg, params, callerFqcn)
    }

    fun Any.logDebug(msg: String, vararg params: Any?) {
        write(javaClass, Log.LogLevel.DEBUG, msg, params, callerFqcn)
    }

    fun Any.logWarn(msg: String, vararg params: Any?) {
        write(javaClass, Log.LogLevel.WARN, msg, params, callerFqcn)
    }

    /** 向支持调用位置的后端传递封装边界；其他 SLF4J 后端沿用普通日志 API。 */
    private fun write(clazz: Class<*>, level: Log.LogLevel, msg: String, params: Array<out Any?>, fqcn: String) {
        val logger = loggers.get(ClassUtils.getUserClass(clazz))
        val enabled = when (level) {
            Log.LogLevel.TRACE -> logger.isTraceEnabled
            Log.LogLevel.DEBUG -> logger.isDebugEnabled
            Log.LogLevel.INFO -> logger.isInfoEnabled
            Log.LogLevel.WARN -> logger.isWarnEnabled
            Log.LogLevel.ERROR -> logger.isErrorEnabled
        }
        if (!enabled) return

        if (logger is LocationAwareLogger) {
            val error = params.lastOrNull() as? Throwable
            val arguments = if (error == null) params else params.copyOf(params.size - 1)
            logger.log(null, fqcn, level.locationAwareLevel, msg, arguments, error)
        } else {
            when (level) {
                Log.LogLevel.TRACE -> logger.trace(msg, *params)
                Log.LogLevel.DEBUG -> logger.debug(msg, *params)
                Log.LogLevel.INFO -> logger.info(msg, *params)
                Log.LogLevel.WARN -> logger.warn(msg, *params)
                Log.LogLevel.ERROR -> logger.error(msg, *params)
            }
        }
    }

    /** 保留已有的按类调用入口，并使用该入口自身作为调用位置边界。 */
    object Log {
        private val callerFqcn = Log::class.java.name

        fun log(clazz: Class<*>, level: LogLevel, msg: String, params: Array<out Any?>) {
            write(clazz, level, msg, params, callerFqcn)
        }

        enum class LogLevel(internal val locationAwareLevel: Int) {
            TRACE(LocationAwareLogger.TRACE_INT),
            DEBUG(LocationAwareLogger.DEBUG_INT),
            INFO(LocationAwareLogger.INFO_INT),
            WARN(LocationAwareLogger.WARN_INT),
            ERROR(LocationAwareLogger.ERROR_INT),
        }
    }
}