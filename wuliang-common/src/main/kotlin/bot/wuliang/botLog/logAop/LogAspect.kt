package bot.wuliang.botLog.logAop

import bot.wuliang.botLog.database.entity.LogEntity
import bot.wuliang.botLog.database.entity.PlatformStats
import bot.wuliang.botLog.database.service.LogService
import bot.wuliang.botLog.database.service.PlatformStatsService
import bot.wuliang.utils.BotUtils

import bot.wuliang.utils.BotUtils.ContextUtil.initializeContext
import com.fasterxml.jackson.core.JsonProcessingException
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.annotation.Pointcut
import org.aspectj.lang.reflect.MethodSignature
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.time.LocalDateTime


/**
 * @description: 日志AOP切面类
 * @author Nature Zero
 * @date 2024/12/16 19:15
 */
@Aspect
@Component
class LogAspect(
    @Autowired private val logService: LogService,
    @Autowired private val platformStatsService: PlatformStatsService
) {

    @Pointcut("@annotation(bot.wuliang.botLog.logAop.SystemLog)")
    fun pc() {
    }

    @Around("pc()")
    fun logRecode(joinPoint: ProceedingJoinPoint): Any? {
        //核心方法执行之前执行的代码
        val start = handleBefore(joinPoint).first
        val logParam = handleBefore(joinPoint).second
        //连接点执行核心方法
        val ret = joinPoint.proceed()
        //核心方法执行完执行的代码
        handleAfter(start, logParam)
        //正常返回结果
        return ret
    }

    //核心方法前业务逻辑代码
    @Throws(JsonProcessingException::class)
    private fun handleBefore(joinPoint: ProceedingJoinPoint): Pair<Long, LogEntity> {
        // 获取功能描述
        val systemLog: SystemLog = getSystemLog(joinPoint)
        val businessName: String = systemLog.businessName
        // 请求对应的类全包名
        val classPath = joinPoint.signature.declaringTypeName
        // 请求对应类方法名
        val methodName = joinPoint.signature.name
        // 请求参数的json形式
        val args = joinPoint.args

        var context: BotUtils.Context? = null
        args.forEach { arg ->
            run {
                if (arg is BotUtils.Context) {
                    val event = arg.getEvent()
                    context = initializeContext(event)
                }
            }
        }

        return Pair(
            System.currentTimeMillis(),
            LogEntity(
                businessName = businessName,
                classPath = classPath,
                methodName = methodName,
                cmdText = context?.message,
                eventType = context?.messageType,
                groupId = context?.groupId,
                userId = context?.userId,
                botId = context?.botId
            )
        )
    }


    private fun handleAfter(start: Long, logParam: LogEntity) {
        // 设置核心方法执行时长
        val end = System.currentTimeMillis()
        val spendTime: Long = end - start
        logParam.costTime = spendTime
        // 日志记录时间
        logParam.createTime = LocalDateTime.now()
        // 往数据库插入日志
        logService.insertLog(logParam)

        val platformStats = PlatformStats(
            // 时间桶，将当前时间格式化为整点小时
            timestamp = LocalDateTime.now().withMinute(0).withSecond(0).withNano(0),
            count = 1
        )

        // 插入或更新 存在对应时间时将count+1
        platformStatsService.insertOrUpdatePlatformStats(platformStats)

    }

    /**
     * @Description 获取核心方法方法上的注解对象
     * @Param :joinPoint
     */
    private fun getSystemLog(joinPoint: ProceedingJoinPoint): SystemLog {
        val methodSignature = joinPoint.signature as MethodSignature
        return methodSignature.method.getAnnotation(SystemLog::class.java)
    }
}