package bot.wuliang.controller

import bot.wuliang.config.CommonConfig.USER_TICKET_KEY
import bot.wuliang.exception.RespBean
import bot.wuliang.exception.RespBeanEnum
import bot.wuliang.otherUtil.CookieUtil
import bot.wuliang.redis.RedisService
import bot.wuliang.user.entity.vo.UserVo
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.*
import java.util.concurrent.TimeUnit
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse


/**
 * @description: 登录控制类
 * @author Nature Zero
 * @date 2024/10/11 23:08
 */
@RestController
@RequestMapping("/login")
class LoginController(
    @Value("\${wuLiang.config.userName}") val manageUserName: String,
    @Value("\${wuLiang.config.password}") val managePassword: String,
    @Autowired val redisService: RedisService,
) {
    @PostMapping("/toLogin")
    fun toLogin(
        @RequestParam("username") username: String,
        @RequestParam("password") password: String,
        httpServletResponse: HttpServletResponse?,
        httpServletRequest: HttpServletRequest?
    ): RespBean {
        if (username == manageUserName && password == managePassword) {
            val ticket = UUID.randomUUID().toString().replace("-", "")

            // 将ticket存储到cookie中
            CookieUtil.setCookie(
                httpServletRequest,
                httpServletResponse!!,
                "userTicket",
                ticket,
                cookieMaxAge = 30 * 24 * 60 * 60
            )

            val userVo = UserVo(
                account = username,
                password = password
            )
            // 将用户对象存储到redis中
            redisService.setValueWithExpiry(USER_TICKET_KEY + ticket, userVo, 30, TimeUnit.DAYS)
            return RespBean.success(ticket)
        } else return RespBean.error(RespBeanEnum.LOGIN_ERROR)
    }

    @PostMapping("/checkUserTicket")
    fun checkUserTicket(@RequestParam("userTicket") userTicket: String): RespBean {
        if (redisService.hasKey(USER_TICKET_KEY + userTicket)) return RespBean.success()
        return RespBean.error(RespBeanEnum.NO_USER)
    }
}