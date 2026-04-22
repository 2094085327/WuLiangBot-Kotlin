package bot.wuliang.adapter.command

import bot.wuliang.distribute.annotation.Executor
import bot.wuliang.otherUtil.PackageScanner
import org.springframework.context.ApplicationContext
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

/**
 * 命令注册中心
 *
 * 负责扫描、注册和管理机器人命令。支持精确匹配和正则表达式匹配两种命令模式。
 * 通过反射机制动态调用标注了 [@Executor][bot.wuliang.distribute.annotation.Executor] 注解的方法。
 */
object CommandRegistry {
    private val commands = ConcurrentHashMap<String, Pair<Class<*>, Method>>()
    private val patternCommands = ConcurrentHashMap<Pattern, Pair<Pair<Class<*>, Method>, String>>()
    private lateinit var applicationContext: ApplicationContext

    /**
     * 初始化命令注册中心
     *
     * @param context Spring应用上下文，用于获取Bean实例
     */
    fun init(context: ApplicationContext) {
        this.applicationContext = context
    }

    /**
     * 扫描指定包路径下的命令类
     *
     * 遍历指定包中的所有类，查找Spring管理的Bean，并扫描其中标注了 [@Executor][bot.wuliang.distribute.annotation.Executor] 注解的方法。
     * 支持精确命令匹配和正则表达式匹配两种模式
     *
     * @param packageNames 要扫描的包名列表
     */
    fun scanCommands(vararg packageNames: String) {
        packageNames.forEach { packageName ->
            object : PackageScanner() {
                override fun dealClass(klass: Class<*>) {
                    if (applicationContext.getBeanNamesForType(klass).isNotEmpty()) {
                        try {
                            scanMethods(klass)
                        } catch (_: Exception) {
                        }
                    }
                }
            }.scanPackage(packageName)
        }
    }

    /**
     * 扫描类中标注了 [@Executor][bot.wuliang.distribute.annotation.Executor] 注解的方法
     *
     * 将方法注册到命令映射表中。如果 action 是有效的正则表达式，则注册为模式匹配命令；
     * 否则注册为精确匹配命令
     *
     * @param clazz 要扫描的类
     */
    private fun scanMethods(clazz: Class<*>) {
        clazz.declaredMethods.forEach { method ->
            if (method.name.contains("$")) return@forEach

            method.getAnnotation(Executor::class.java)?.let { executor ->
                val commandPattern = executor.action
                try {
                    val pattern = Pattern.compile(commandPattern)
                    patternCommands[pattern] = Pair(Pair(clazz, method), commandPattern)
                } catch (_: Exception) {
                    commands[commandPattern] = Pair(clazz, method)
                }
            }
        }
    }

    /**
     * 根据命令字符串获取匹配的命令结果
     *
     * 首先尝试精确匹配，如果未找到则遍历所有正则表达式模式进行匹配
     *
     * @param command 命令字符串
     * @return 匹配成功的命令结果，包含命令对象、匹配器和匹配信息；未匹配时返回 null
     */
    fun getCommandWithMatcher(command: String): CommandMatchResult? {
        commands[command]?.let { (clazz, method) ->
            val bean = applicationContext.getBean(clazz)
            val dummyMatcher = Pattern.compile("").matcher("")
            return CommandMatchResult(ReflectiveBotCommand(bean, method), dummyMatcher, false, command)
        }

        patternCommands.forEach { (pattern, pair) ->
            val matcher = pattern.matcher(command)
            if (matcher.matches()) {
                val (clazzToMethod, regex) = pair
                val (clazz, method) = clazzToMethod
                val bean = applicationContext.getBean(clazz)
                return CommandMatchResult(ReflectiveBotCommand(bean, method), matcher, true, regex)
            }
        }
        return null
    }
}