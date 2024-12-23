package bot.demo.txbot.config

import bot.demo.txbot.common.exception.RespBean
import bot.demo.txbot.common.exception.RespBeanEnum
import bot.demo.txbot.common.utils.RedisService
import bot.demo.txbot.other.CookieUtil
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.web.servlet.HandlerInterceptor
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse


/**
 * @description: 自定义的登录拦截器
 * @author Nature Zero
 * @date 2024/10/11 22:33
 */
class LoginHandlerInterceptor(
    private var redisService: RedisService
) : HandlerInterceptor {
    private val objectMapper = ObjectMapper()

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        // 1、从请求头中获取token
        val token: String? = CookieUtil.getCookieValue(request, "userTicket")
        // 2、判断 token 是否存在
        if (token.isNullOrEmpty()) {
            // 拦截请求并返回信息给前台 （前台后置拦截器就是根据这里面返回的json数据，来判读并跳转到登录界面）
            response.contentType = "application/json; charset=utf-8"
            response.writer.print(objectMapper.writeValueAsString(RespBean.error(RespBeanEnum.NO_USER)))
            return false
        }

        if (redisService.getValue("users:$token") == null) {
            // 拦截请求并返回信息给前台 （前台后置拦截器就是根据这里面返回的json数据，来判读并跳转到登录界面）
            response.contentType = "application/json; charset=utf-8"
            response.writer.print(objectMapper.writeValueAsString(RespBean.error(RespBeanEnum.NO_USER)))
            return false
        } else {
            return true
        }
    }
}