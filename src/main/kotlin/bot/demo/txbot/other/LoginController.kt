package bot.demo.txbot.other

import bot.demo.txbot.common.exception.RespBean
import bot.demo.txbot.common.exception.RespBeanEnum
import bot.demo.txbot.common.utils.RedisService
import bot.demo.txbot.other.vo.UserVo
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody
import java.util.*
import java.util.concurrent.TimeUnit
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse


/**
 * @description: 登录控制类
 * @author Nature Zero
 * @date 2024/10/11 23:08
 */
@Controller
@RequestMapping("/login")
class LoginController(
    @Value("\${wuLiang.config.userName}") val manageUserName: String,
    @Value("\${wuLiang.config.password}") val managePassword: String,
    @Autowired val redisService: RedisService,
) {
    @PostMapping("/toLogin")
    @ResponseBody
    fun toLogin(
        @RequestParam("username") username: String,
        @RequestParam("password") password: String,
        httpServletResponse: HttpServletResponse?,
        httpServletRequest: HttpServletRequest?
    ): RespBean {
        if (username == manageUserName && password == managePassword) {
            val ticket = UUID.randomUUID().toString().replace("-", "")

            // 将ticket存储到cookie中
            CookieUtil.setCookie(httpServletRequest, httpServletResponse!!, "userTicket", ticket)

            val userVo = UserVo(
                account = username,
                password = password
            )
            // 将用户对象存储到redis中
            redisService.setValueWithExpiry("users:$ticket", userVo,30,TimeUnit.DAYS)
            return RespBean.success(ticket)
        } else return RespBean.error(RespBeanEnum.LOGIN_ERROR)
    }

}