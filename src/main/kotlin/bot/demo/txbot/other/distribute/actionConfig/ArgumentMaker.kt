package bot.demo.txbot.other.distribute.actionConfig

import bot.demo.txbot.common.utils.JacksonUtil


/**
 * @description: 参数构造器
 * @author Nature Zero
 * @date 2024/8/27 22:14
 */
class ArgumentMaker {
    // 注解AParameter中name的值 +  参数对象转换成的gson字符串所形成的map
    private var argumentMap: MutableMap<String, String>? = HashMap()

    // 其name就是注解AParameter中name的值，value就是参数的具体值
    fun add(name: String, value: Any): ArgumentMaker {
        // 转换为Json字符串
        argumentMap!![name] = JacksonUtil.toJsonString(value)
        return this
    }

    // 将得到的name + 参数对象转换成的gson字符串map再次转换成gson字符串，以便于进行传输
    fun toOgnl(): String? {
        if (argumentMap!!.isEmpty()) {
            return null
        }

        return JacksonUtil.toJsonString(argumentMap!!)
    }
}