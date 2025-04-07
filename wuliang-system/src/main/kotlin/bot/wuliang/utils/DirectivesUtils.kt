package bot.wuliang.utils

import bot.wuliang.botLog.logUtil.LoggerUtils.logInfo
import bot.wuliang.config.DirectivesConfig.DIRECTIVES_KEY
import bot.wuliang.redis.RedisService
import bot.wuliang.service.DirectivesService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import javax.annotation.PostConstruct

@Component
class DirectivesUtils {
    @Autowired
    lateinit var redisService: RedisService

    @Autowired
    lateinit var directivesService: DirectivesService

    @PostConstruct
    fun initRedisCacheDirectives() {
        if (!redisService.hasKey(DIRECTIVES_KEY)) {
            val selectDirectivesList = directivesService.selectDirectivesList(null)
            if (selectDirectivesList.isNotEmpty()) {
                redisService.setValue(DIRECTIVES_KEY, selectDirectivesList)
                logInfo("指令缓存初始化完成")
            }
        }
    }
}