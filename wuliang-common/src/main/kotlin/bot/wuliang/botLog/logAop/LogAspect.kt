package bot.wuliang.botLog.logAop

import bot.wuliang.botUtil.BotUtils
import bot.wuliang.botUtil.vo.ContextVo

import bot.wuliang.botLog.database.service.impl.LogServiceImpl
import bot.wuliang.botUtil.BotUtils.ContextUtil.createContextVo
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
class LogAspect {
    @Autowired
    private lateinit var logService: LogServiceImpl

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

        var cmdText: String? = null
        var contextVo: ContextVo? = null
        args.forEach { arg ->
            run {
                if (arg is BotUtils.Context) {
                    val event = arg.getEvent()
                    cmdText = event.message
                    contextVo = arg.createContextVo()
                }
            }
        }

        return Pair(
            System.currentTimeMillis(),
            LogEntity(
                businessName = businessName,
                classPath = classPath,
                methodName = methodName,
                cmdText = cmdText,
                eventType = contextVo?.messageType,
                groupId = contextVo?.groupId,
                userId = contextVo?.userId,
                botId = contextVo?.botId
            )
        )
    }


    @Throws(JsonProcessingException::class)
    private fun handleAfter(start: Long, logParam: LogEntity) {
        // 设置核心方法执行时长
        val end = System.currentTimeMillis()
        val spendTime: Long = end - start
        logParam.costTime = spendTime
        // 日志记录时间
        logParam.createTime = LocalDateTime.now()
        // 往数据库插入日志
        logService.insertLog(logParam)
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