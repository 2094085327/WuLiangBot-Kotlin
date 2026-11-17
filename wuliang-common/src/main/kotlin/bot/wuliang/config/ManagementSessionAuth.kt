package bot.wuliang.config

import bot.wuliang.otherUtil.CookieUtil
import bot.wuliang.redis.RedisService
import bot.wuliang.user.entity.vo.UserVo
import org.springframework.stereotype.Component
import javax.servlet.http.HttpServletRequest

@Component
class ManagementSessionAuth(private val redisService: RedisService) {
    fun ticket(request: HttpServletRequest): String? = CookieUtil.getCookieValue(request, "userTicket")

    fun isValid(ticket: String?): Boolean {
        if (ticket.isNullOrBlank() || ticket.length > 128) return false
        return runCatching {
            redisService.getValueOrThrow(CommonConfig.USER_TICKET_KEY + ticket) is UserVo
        }.getOrDefault(false)
    }

    companion object {
        const val TICKET_ATTRIBUTE = "managementTicket"
    }
}