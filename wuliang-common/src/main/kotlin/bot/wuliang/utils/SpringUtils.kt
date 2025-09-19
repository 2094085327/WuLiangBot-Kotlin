package bot.wuliang.utils

import org.springframework.aop.framework.AopContext
import org.springframework.beans.BeansException
import org.springframework.beans.factory.NoSuchBeanDefinitionException
import org.springframework.beans.factory.config.BeanFactoryPostProcessor
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.stereotype.Component

/**
 * Spring 工具类
 * 用于在非 Spring 管理的环境中获取 Bean
 */
@Component
object SpringUtils : BeanFactoryPostProcessor, ApplicationContextAware {

    /** Spring 应用上下文环境 */
    private lateinit var beanFactory: ConfigurableListableBeanFactory
    private lateinit var applicationContext: ApplicationContext

    @Throws(BeansException::class)
    override fun postProcessBeanFactory(beanFactory: ConfigurableListableBeanFactory) {
        SpringUtils.beanFactory = beanFactory
    }

    @Throws(BeansException::class)
    override fun setApplicationContext(applicationContext: ApplicationContext) {
        SpringUtils.applicationContext = applicationContext
    }

    /**
     * 获取对象
     *
     * @param name Bean 名称
     * @return Bean 实例
     */
    @Suppress("UNCHECKED_CAST")
    @Throws(BeansException::class)
    fun <T> getBean(name: String): T {
        return beanFactory.getBean(name) as T
    }

    /**
     * 获取类型为 requiredType 的对象
     *
     * @param clz Bean 类型
     * @return Bean 实例
     */
    @Throws(BeansException::class)
    fun <T> getBean(clz: Class<T>): T {
        return beanFactory.getBean(clz)
    }

    /**
     * 检查是否包含指定名称的 Bean
     *
     * @param name Bean 名称
     * @return 是否存在该 Bean
     */
    fun containsBean(name: String): Boolean {
        return beanFactory.containsBean(name)
    }

    /**
     * 判断以给定名字注册的 Bean 定义是单例还是原型
     *
     * @param name Bean 名称
     * @return 是否为单例
     * @throws NoSuchBeanDefinitionException 如果没有找到对应的 Bean
     */
    @Throws(NoSuchBeanDefinitionException::class)
    fun isSingleton(name: String): Boolean {
        return beanFactory.isSingleton(name)
    }

    /**
     * 获取注册对象的类型
     *
     * @param name Bean 名称
     * @return Bean 类型
     * @throws NoSuchBeanDefinitionException 如果没有找到对应的 Bean
     */
    @Throws(NoSuchBeanDefinitionException::class)
    fun getType(name: String): Class<*>? {
        return beanFactory.getType(name)
    }

    /**
     * 获取给定 Bean 名称的所有别名
     *
     * @param name Bean 名称
     * @return 别名数组
     * @throws NoSuchBeanDefinitionException 如果没有找到对应的 Bean
     */
    @Throws(NoSuchBeanDefinitionException::class)
    fun getAliases(name: String): Array<String> {
        return beanFactory.getAliases(name)
    }

    /**
     * 获取 AOP 代理对象
     *
     * @param invoker 当前对象
     * @return AOP 代理对象
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> getAopProxy(invoker: T): T {
        return AopContext.currentProxy() as T
    }

    /**
     * 获取指定名称的 AOP 代理对象
     *
     * @param name Bean 名称
     * @return AOP 代理对象
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> getAopBean(name: String): T {
        return applicationContext.getBean(name) as T
    }

    /**
     * 获取指定类型的 AOP 代理对象
     *
     * @param clz Bean 类型
     * @return AOP 代理对象
     */
    fun <T> getAopBean(clz: Class<T>): T {
        return applicationContext.getBean(clz)
    }

    /**
     * 获取当前的激活环境配置
     *
     * @return 激活的环境配置数组
     */
    fun getActiveProfiles(): Array<String> {
        return applicationContext.environment.activeProfiles
    }

    /**
     * 获取当前激活的第一个环境配置
     *
     * @return 激活的环境配置（如果有多个，仅返回第一个）
     */
    fun getActiveProfile(): String? {
        return getActiveProfiles().firstOrNull()
    }
}
