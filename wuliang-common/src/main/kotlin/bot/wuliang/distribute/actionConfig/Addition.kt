package bot.wuliang.distribute.actionConfig

import bot.wuliang.distribute.annotation.AParameter
import bot.wuliang.botUtil.BotUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Component
import java.lang.reflect.Method
import java.util.regex.Matcher


/**
 * @description: 请求分发
 * @author Nature Zero
 * @date 2024/8/27 21:58
 */
@Component
class Addition(
    @Autowired private val actionFactory: ActionFactory,
    @Autowired private val applicationContext: ApplicationContext
) {

    fun doRequest(action: String, context: BotUtils.Context): String {
        val ad: ActionDefinition = actionFactory.newInstance().getActionDefinition(action)
            ?: throw IllegalAccessException("方法 $action 未定义")

        // 通过 Spring ApplicationContext 获取 Bean 实例，确保依赖注入正常工作
        val beanClass = ad.getObject()::class.java
        val `object` = applicationContext.getBean(beanClass)
        val method: Method = ad.getMethod()

        // 检查方法上是否有 @AParameter 注解
        val parameters = if (method.isAnnotationPresent(AParameter::class.java)) {
            getParameterArr(context, ad.getMatched(), method)
        } else {
            throw IllegalAccessException("参数注解 ${AParameter::class.java.name} 缺失")
        }

        // 设置方法可访问，解决IllegalAccessException问题
        method.isAccessible = true

        // 处理 suspend 函数
        val result = if (isSuspendFunction(method)) {
            // 如果是 suspend 函数，使用协程执行
            runBlocking(Dispatchers.Default) {
                method.invoke(`object`, *parameters)
            }
        } else {
            // 普通函数直接执行
            method.invoke(`object`, *parameters)
        }
        return result?.toString() ?: ""
    }

    private fun isSuspendFunction(method: Method): Boolean {
        // suspend 函数的最后一个参数类型是 Continuation
        return method.parameterTypes.isNotEmpty() &&
                method.parameterTypes.last().simpleName == "Continuation"
    }

    private fun getParameterArr(
        context: BotUtils.Context,
        matcher: Matcher?,
        method: Method
    ): Array<Any?> {
        val parameters = method.parameters
        val results = arrayOfNulls<Any>(parameters.size)

        parameters.forEachIndexed { index, parameter ->
            results[index] = when (parameter.type) {
                BotUtils.Context::class.java -> context
                Matcher::class.java -> matcher
                else -> null
            }
        }
        return results
    }

}