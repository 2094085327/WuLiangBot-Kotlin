package bot.wuliang.distribute.actionConfig

import bot.wuliang.distribute.annotation.AParameter
import bot.wuliang.distribute.annotation.ActionService
import bot.wuliang.distribute.annotation.Executor
import bot.wuliang.otherUtil.PackageScanner
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Component
import java.lang.reflect.Method
import java.lang.reflect.Parameter


/**
 * @description: 动态注册方法
 * @author Nature Zero
 * @date 2024/8/27 下午9:37
 */
@Component
class ActionFactory(@Autowired private var applicationContext: ApplicationContext) {
    private val actionDefinitionMap: MutableMap<String, ActionDefinition?> = HashMap()

    fun newInstance(): ActionFactory {
        // 使用 Spring 的 ApplicationContext 获取 ActionFactory 实例
        return applicationContext.getBean(ActionFactory::class.java)
    }

    fun scanAction(klass: Class<*>) {
        scanAction(klass.getPackage().name)
    }

    private fun scanAction(packageName: String) {
        object : PackageScanner() {
            override fun dealClass(klass: Class<*>) {
                if (!klass.isAnnotationPresent(ActionService::class.java)) {
                    return
                }
                try {
                    // 从 Spring 容器中获取 Bean
                    val bean = applicationContext.getBean(klass)
                    scanMethod(klass, bean)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }.scanPackage(packageName)
    }

    private fun scanAction(`object`: Any) {
        try {
            scanMethod(`object`.javaClass, `object`)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Throws(Exception::class)
    private fun scanMethod(klass: Class<*>, `object`: Any) {
        val methods = klass.declaredMethods

        for (method in methods) {
            if (!method.isAnnotationPresent(Executor::class.java)) {
                continue
            }
            val executor: Executor = method.getAnnotation(Executor::class.java)
            val action: String = executor.action

            // 允许覆盖现有定义
//            if (actionDefinitionMap[action] != null) logInfo("覆盖式生成方法 $action")

            val parameterList: MutableList<Parameter> = ArrayList()
            // 检查方法上是否有 @AParameter 注解
            if (method.isAnnotationPresent(AParameter::class.java)) {
                // 如果方法上有 @AParameter 注解，则直接添加所有参数
                parameterList.addAll(method.parameters)
            }

            addActionDefinition(action, klass, `object`, method, parameterList)
        }
    }


    private fun addActionDefinition(
        action: String,
        klass: Class<*>,
        `object`: Any,
        method: Method,
        parameterList: List<Parameter>
    ) {
        val actionDefinition = ActionDefinition(klass, `object`, method, parameterList, action)
        actionDefinitionMap[action] = actionDefinition
    }

    fun getActionDefinition(action: String?): ActionDefinition? {
        actionDefinitionMap.values.forEach { def ->
            if (def == null) return null
            if (def.matches(action)) return def
        }
        return null
    }
}