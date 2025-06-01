package bot.wuliang.config

import bot.wuliang.redis.RedisService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Configuration
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.servlet.config.annotation.InterceptorRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer


/**
 * @description: MVC配置类
 * @author Nature Zero
 * @date 2024/10/11 22:49
 */
@Configuration
class WebConfig : WebMvcConfigurer {
    @Autowired
    private lateinit var redisService: RedisService

    @Autowired
    private lateinit var userArgumentResolver: UserArgumentResolver

    override fun addArgumentResolvers(resolvers: MutableList<HandlerMethodArgumentResolver?>) {
        resolvers.add(userArgumentResolver)
    }

    /**
     * 添加拦截器
     *
     * @param registry
     */
    override fun addInterceptors(registry: InterceptorRegistry) {
        val interceptorRegistration = registry.addInterceptor(LoginHandlerInterceptor(redisService))
        interceptorRegistration.excludePathPatterns("/asserts/**", "/error/**", "/index.html", "/", "/login/toLogin","/directives/list")
        interceptorRegistration.addPathPatterns(
            "/restartManage",
            "/warframe/wfManage/**",
            "/restartManage",
            "/dailyJson",
            "/botConfig/**",
            "/category/**",
            "/directives/**"
        )
    }
}
