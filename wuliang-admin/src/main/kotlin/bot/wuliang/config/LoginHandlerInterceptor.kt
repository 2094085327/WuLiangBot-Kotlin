package bot.wuliang.config

import bot.wuliang.exception.RespBean
import bot.wuliang.exception.RespBeanEnum
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
    private val sessionAuth: ManagementSessionAuth
) : HandlerInterceptor {
    private val objectMapper = ObjectMapper()

    override fun preHandle(request: HttpServletRequest, response: HttpServletResponse, handler: Any): Boolean {
        if (sessionAuth.isValid(sessionAuth.ticket(request))) return true
        response.contentType = "application/json; charset=utf-8"
        response.writer.print(objectMapper.writeValueAsString(RespBean.error(RespBeanEnum.NO_USER)))
        return false
    }
}