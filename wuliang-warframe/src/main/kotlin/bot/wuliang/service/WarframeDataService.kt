package bot.wuliang.service

import bot.wuliang.config.WARFRAME_STATUS_URL
import bot.wuliang.config.WfMarketConfig.WF_FISSURE_KEY
import bot.wuliang.httpUtil.HttpUtil
import bot.wuliang.moudles.Fissure
import bot.wuliang.redis.RedisService
import bot.wuliang.utils.ParseDataUtil
import kotlinx.coroutines.runBlocking
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class WarframeDataService {
    @Autowired
    private lateinit var redisService: RedisService

    @Autowired
    private lateinit var parseDataUtil: ParseDataUtil

    fun getFissuresData(): List<Fissure?>? {
        if (!redisService.hasKey(WF_FISSURE_KEY)) {
            val data = HttpUtil.doGetJson(WARFRAME_STATUS_URL)
            runBlocking {
               return@runBlocking parseDataUtil.parseFissure(data["ActiveMissions"], data["VoidStorms"])
            }
        }
        return redisService.getValueTyped(WF_FISSURE_KEY)
    }
}