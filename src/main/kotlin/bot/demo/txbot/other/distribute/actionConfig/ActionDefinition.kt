package bot.demo.txbot.other.distribute.actionConfig

import java.lang.reflect.Method
import java.lang.reflect.Parameter
import java.util.regex.Matcher
import java.util.regex.Pattern


/**
 * @description: 获取方法信息
 * @author Nature Zero
 * @date 2024/8/27 下午9:33
 */
open class ActionDefinition(// 该方法所对应的类
    private var klass: Class<*>?,// 执行该方法的对象
    private var `object`: Any,// 该方法
    private var method: Method,// 该方法的所有参数
    private var parameterList: List<Parameter>,
    action: String
) {
    private var pattern: Pattern? = null // 正则表达式模式
    private var matcher: Matcher? = null // 实际匹配到的指令

    init {
        this.pattern = Pattern.compile(action)
    }

    protected fun getKlass(): Class<*>? {
        return klass
    }

    fun getObject(): Any {
        return `object`
    }

    fun getMethod(): Method {
        return method
    }

    fun getParameterList(): List<Parameter> {
        return parameterList
    }

    fun matches(action: String?): Boolean {
        matcher = this.pattern?.matcher(action)
        return matcher?.matches() ?: false
    }

    fun getMatched(): Matcher? {
        return matcher
    }
}