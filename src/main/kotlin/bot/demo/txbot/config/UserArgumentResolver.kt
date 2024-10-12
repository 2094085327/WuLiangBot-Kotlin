package bot.demo.txbot.config

import bot.demo.txbot.common.database.user.UserServiceImpl
import bot.demo.txbot.common.utils.RedisService
import bot.demo.txbot.other.CookieUtil
import bot.demo.txbot.other.vo.UserVo
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.annotation.Configuration
import org.springframework.core.MethodParameter
import org.springframework.web.bind.support.WebDataBinderFactory
import org.springframework.web.context.request.NativeWebRequest
import org.springframework.web.method.support.HandlerMethodArgumentResolver
import org.springframework.web.method.support.ModelAndViewContainer
import org.thymeleaf.util.StringUtils
import javax.servlet.http.HttpServletRequest


/**
 * @description: 用户参数解析器类，用于将用户信息解析为User对象
 * @author Nature Zero
 * @date 2024/10/11 22:52
 */
@Configuration
class UserArgumentResolver : HandlerMethodArgumentResolver {
    // 注入用户服务，用于用户相关的业务逻辑处理
    @Autowired
    private lateinit var userService: UserServiceImpl

    @Autowired
    private lateinit var redisService: RedisService

    // 检查是否支持给定的参数
    override fun supportsParameter(parameter: MethodParameter): Boolean {
        // 获取参数的类型
        val parameterType = parameter.parameterType
        // 判断参数类型是否为User类，是则返回true，表示支持
        return parameterType == UserVo::class.java
    }

    // 解析用户参数
    override fun resolveArgument(
        parameter: MethodParameter,
        mavContainer: ModelAndViewContainer?,
        webRequest: NativeWebRequest,
        binderFactory: WebDataBinderFactory?
    ): Any? {
        // 获取原生的HTTP请求和响应对象
        val nativeRequest = webRequest.getNativeRequest(HttpServletRequest::class.java)

        // 从请求的Cookie中获取用户票据（userTicket）
        val userTicket: String? = nativeRequest?.let { CookieUtil.getCookieValue(it, "userTicket") }


        // 如果用户票据为空，则返回null
        if (StringUtils.isEmpty(userTicket)) {
            return null
        }

        return redisService.getValue("users:$userTicket")
    }
}
