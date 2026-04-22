package bot.wuliang.adapter.command

import bot.wuliang.adapter.context.ExecutionContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.lang.reflect.Method
import java.util.regex.Matcher
import kotlin.coroutines.resumeWithException


/**
 * 基于反射的命令执行器
 *
 * 通过反射机制调用标注了 [@Executor][bot.wuliang.distribute.annotation.Executor] 注解的方法，
 * 自动注入所需的参数（如 ExecutionContext、MessageSender、Matcher 等）
 * 支持 suspend 函数的异步调用
 *
 * @property bean Spring Bean 实例
 * @property method 要执行的方法
 */
class ReflectiveBotCommand(private val bean: Any, private val method: Method) : BotCommand {
    override suspend fun execute(context: ExecutionContext, matcher: Matcher?): Any? {
        val params = method.parameters
        val isSuspend = isSuspendFunction(method)

        val paramCount = if (isSuspend) params.size - 1 else params.size
        val parameters: Array<Any?> = arrayOfNulls(paramCount)

        for (i in 0 until paramCount) {
            val paramType = params[i].type
            when (paramType) {
                ExecutionContext::class.java -> parameters[i] = context
                Matcher::class.java -> parameters[i] = matcher
                else -> parameters[i] = null
            }
        }

        return invokeMethod(method, bean, parameters, isSuspend)
    }

    /**
     * 调用方法（支持同步和异步）
     *
     * 对于 suspend 函数，在 Dispatchers.Default 调度器中执行；
     * 对于普通函数，直接通过反射调用
     *
     * @param method 要调用的方法
     * @param bean Bean 实例
     * @param parameters 方法参数数组
     * @param isSuspend 是否为 suspend 函数
     * @return 方法返回值
     * @throws RuntimeException 当方法执行失败时抛出
     */
    private suspend fun invokeMethod(method: Method, bean: Any, parameters: Array<Any?>, isSuspend: Boolean): Any? {
        method.isAccessible = true

        return try {
            if (isSuspend) {
                withContext(Dispatchers.Default) {
                    callSuspendMethod(bean, method, parameters)
                }
            } else {
                method.invoke(bean, *parameters)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            throw RuntimeException("命令执行失败: ${method.name}", e.cause ?: e)
        }
    }


    /**
     * 判断方法是否为 suspend 函数
     *
     * Kotlin 的 suspend 函数会在参数列表末尾自动添加 Continuation 参数
     *
     * @param method 要判断的方法
     * @return 如果是 suspend 函数返回 true，否则返回 false
     */
    private fun isSuspendFunction(method: Method): Boolean {
        return method.parameterTypes.isNotEmpty() &&
                method.parameterTypes.last().name == "kotlin.coroutines.Continuation"
    }

    /**
     * 调用 suspend 方法
     *
     * 通过 suspendCancellableCoroutine 将 Kotlin 协程挂起函数转换为可取消的协程
     * 自动在参数数组末尾添加 Continuation 对象
     *
     * @param bean Bean 实例
     * @param method 要调用的 suspend 方法
     * @param parameters 方法参数数组（不包含 Continuation）
     * @return 方法返回值
     * @throws Exception 当方法执行失败时抛出异常
     */
    private suspend fun callSuspendMethod(
        bean: Any,
        method: Method,
        parameters: Array<Any?>
    ): Any? = suspendCancellableCoroutine { continuation ->
        try {
            val suspendParams = parameters.copyOf(parameters.size + 1)
            suspendParams[suspendParams.size - 1] = continuation

            method.invoke(bean, *suspendParams)
        } catch (ex: Exception) {
            val cause = ex.cause ?: ex
            continuation.resumeWithException(cause)
        }
    }
}