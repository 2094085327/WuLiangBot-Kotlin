package bot.demo.txbot.other.distribute.actionConfig

import bot.demo.txbot.other.distribute.annotation.AParameter
import com.mikuac.shiro.core.Bot
import com.mikuac.shiro.dto.event.message.MessageEvent
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Component
import java.lang.reflect.Method
import java.lang.reflect.Parameter
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
    @Throws(Exception::class)
    fun doRequest(action: String, bot: Bot, event: MessageEvent): String {
        val ad: ActionDefinition = actionFactory.newInstance().getActionDefinition(action)
            ?: throw IllegalAccessException("方法 $action 未定义")

        val `object`: Any = ad.getObject()
        val method: Method = ad.getMethod()

        // 获取原有的参数列表
        val matcher = ad.getMatched()
        val parameters = getParameterArr(bot, event, matcher, ad.getParameterList())

        val result = method.invoke(`object`, *parameters)
        return result?.toString() ?: ""
    }

    private fun getParameterArr(
        bot: Bot,
        event: MessageEvent,
        matcher: Matcher?,
        parameterList: List<Parameter>
    ): Array<Any?> {
        val results = arrayOfNulls<Any>(parameterList.size)

        parameterList.forEachIndexed { index, parameter ->
            val key = parameter.getAnnotation(AParameter::class.java).name
            results[index] = when (key) {
                "bot" -> bot
                "event" -> event
                "matcher" -> matcher

                else -> null
            }
        }
        return results
    }

}