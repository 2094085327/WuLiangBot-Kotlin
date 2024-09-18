package bot.demo.txbot.other.distribute.actionConfig

import bot.demo.txbot.common.botUtil.BotUtils.Context
import bot.demo.txbot.other.distribute.annotation.AParameter
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
    @Autowired private var applicationContext: ApplicationContext,
    @Autowired private val actionFactory: ActionFactory
) {

    //    fun doRequest(action: String, bot: Bot, event: MessageEvent): String {
    fun doRequest(action: String, context: Context): String {
        val ad: ActionDefinition = actionFactory.newInstance().getActionDefinition(action)
            ?: throw IllegalAccessException("方法 $action 未定义")

        val `object`: Any = ad.getObject()
        val method: Method = ad.getMethod()

        // 检查方法上是否有 @AParameter 注解
        val parameters = if (method.isAnnotationPresent(AParameter::class.java)) {
//            getParameterArr(bot, event, ad.getMatched(), method)
            getParameterArr(context, ad.getMatched(), method)
        } else {
            throw IllegalAccessException("参数注解 ${AParameter::class.java.name} 缺失")
        }

        val result = method.invoke(`object`, *parameters)
        return result?.toString() ?: ""
    }

    private fun getParameterArr(
//        bot: Bot,
//        event: MessageEvent,
        context: Context,
        matcher: Matcher?,
        method: Method
    ): Array<Any?> {
        val parameters = method.parameters
        val results = arrayOfNulls<Any>(parameters.size)

        parameters.forEachIndexed { index, parameter ->
            results[index] = when (parameter.type) {
//                Bot::class.java -> bot
//                MessageEvent::class.java, AnyMessageEvent::class.java, PrivateMessageEvent::class.java, GroupMessageEvent::class.java -> event

                Context::class.java -> context
                Matcher::class.java -> matcher
                else -> null
            }
        }
        return results
    }

}