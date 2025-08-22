package bot.wuliang.command


import bot.wuliang.utils.BotUtils
import bot.wuliang.distribute.annotation.Executor
import bot.wuliang.otherUtil.PackageScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import org.springframework.context.ApplicationContext
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Matcher
import java.util.regex.Pattern
import kotlin.coroutines.resumeWithException

/**
 * 命令自动化注册中心
 */
/**
 * 命令自动化注册中心
 */
object CommandRegistry {
    private val commands = ConcurrentHashMap<String, Pair<Class<*>, Method>>() // 存储类和方法
    private val patternCommands =
        ConcurrentHashMap<Pattern, Pair<Pair<Class<*>, Method>, String>>() // 存储Pattern和原始regex
    private lateinit var applicationContext: ApplicationContext


    /**
     * 初始化注册中心
     * @param context Spring 应用上下文
     */
    fun init(context: ApplicationContext) {
        this.applicationContext = context
    }


    /**
     * 扫描并注册所有命令
     * @param packageNames 要扫描的包名列表
     */
    fun scanCommands(vararg packageNames: String) {
        packageNames.forEach { packageName ->
            object : PackageScanner() {
                override fun dealClass(klass: Class<*>) {
                    // 检查类是否是 Spring 管理的 Bean
                    if (applicationContext.getBeanNamesForType(klass).isNotEmpty()) {
                        try {
                            // 这里不立即获取Bean实例，而是在需要时获取
                            scanMethods(klass)
                        } catch (e: Exception) {
                            // 如果无法处理类，则跳过
                        }
                    }
                }
            }.scanPackage(packageName)
        }
    }

    /**
     * 扫描类中的方法
     */
    private fun scanMethods(clazz: Class<*>) {
        clazz.declaredMethods.forEach { method ->
            method.getAnnotation(Executor::class.java)?.let { executor ->
                val commandPattern = executor.action
                try {
                    // 尝试将action解析为正则表达式
                    val pattern = Pattern.compile(commandPattern)
                    patternCommands[pattern] = Pair(Pair(clazz, method), commandPattern)
                } catch (e: Exception) {
                    // 如果不是有效的正则表达式，作为普通命令处理
                    commands[commandPattern] = Pair(clazz, method)
                }
            }
        }
    }


    /**
     * 获取匹配的命令和匹配器
     * @param command 指令文本
     * @return Triple<BotCommand, Matcher, Boolean> 命令、匹配器和是否为正则匹配，未找到时返回 null
     */
    fun getCommandWithMatcher(command: String): Triple<BotCommand, Matcher, Boolean>? {
        // 首先尝试精确匹配
        commands[command]?.let { (clazz, method) ->
            val bean = applicationContext.getBean(clazz)
            val dummyMatcher = Pattern.compile("").matcher("")
            return Triple(ReflectiveBotCommand(bean, method), dummyMatcher, false)
        }

        // 然后尝试正则表达式匹配
        patternCommands.forEach { (pattern, pair) ->
            val matcher = pattern.matcher(command)
            if (matcher.matches()) {
                val (clazzToMethod, _) = pair
                val (clazz, method) = clazzToMethod
                val bean = applicationContext.getBean(clazz)
                return Triple(ReflectiveBotCommand(bean, method), matcher, true)
            }
        }

        return null
    }


    /**
     * 反射实现的命令包装器
     */
    class ReflectiveBotCommand(
        private val bean: Any,
        private val method: Method
    ) : BotCommand {
        // Spring 上下文引用
        private val applicationContext = CommandRegistry.applicationContext

        override suspend fun execute(context: BotUtils.Context): String {
            // 实时获取 Bean 实例
            val targetBean = applicationContext.getBean(bean.javaClass)
            val parameters = getParameterArr(context, method, null)
            return invokeMethod(method, targetBean, parameters)
        }

        override fun execute(context: BotUtils.Context, matcher: Matcher): String {
            // 实时获取 Bean 实例
            val targetBean = applicationContext.getBean(bean.javaClass)
            val parameters = getParameterArr(context, method, matcher)
            return invokeMethod(method, targetBean, parameters)
        }

        private fun invokeMethod(method: Method, bean: Any, parameters: Array<Any?>): String {
            try {
                // 设置方法可访问
                method.isAccessible = true

                return if (isSuspendFunction(method)) {
                    // 对于suspend函数，使用专门的调用方法确保在适当的协程上下文中执行
                    runBlocking(Dispatchers.Default) { callSuspendMethod(bean, method, parameters) }
                } else {
                    method.invoke(bean, *parameters)?.toString() ?: ""
                }
            } catch (e: IllegalAccessException) {
                // 处理访问权限问题
                throw RuntimeException("无法访问方法: ${method.name}", e)
            }
        }
    }


    /**
     * 构建方法调用参数数组
     */
    private fun getParameterArr(context: BotUtils.Context, method: Method, matcher: Matcher?): Array<Any?> {
        val params = method.parameters
        val result = arrayOfNulls<Any?>(params.size)

        // 对于suspend函数，最后一个参数是Continuation
        val isSuspend = isSuspendFunction(method)
        val loopSize = if (isSuspend) params.size - 1 else params.size

        for (i in 0 until loopSize) {
            result[i] = when (params[i].type) {
                BotUtils.Context::class.java -> context
                Matcher::class.java -> matcher
                else -> null
            }
        }

        return result
    }

    /**
     * 判断方法是否为挂起函数
     */
    private fun isSuspendFunction(method: Method): Boolean {
        return method.parameterTypes.isNotEmpty() &&
                method.parameterTypes.last().name == "kotlin.coroutines.Continuation"
    }

    /**
     * 调用挂起函数（反射方式）
     */
    private suspend fun callSuspendMethod(
        bean: Any,
        method: Method,
        parameters: Array<Any?>
    ): String = suspendCancellableCoroutine { continuation ->
        try {
            // 对于suspend函数，最后一个参数必须是Continuation
            // 将传入的continuation作为最后一个参数
            val suspendParams = parameters.copyOf(parameters.size + 1)
            suspendParams[suspendParams.size - 1] = continuation

            // 执行反射调用
            method.invoke(bean, *suspendParams)

        } catch (ex: Exception) {
            // 处理反射调用异常
            val cause = ex.cause ?: ex
            continuation.resumeWithException(cause)
        }
    }
}