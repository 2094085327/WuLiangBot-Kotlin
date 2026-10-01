package bot.wuliang.service

import bot.wuliang.config.WARFRAME_STATUS_URL
import bot.wuliang.config.WfMarketConfig.WF_FISSURE_KEY
import bot.wuliang.httpUtil.HttpUtil
import bot.wuliang.moudles.Fissure
import bot.wuliang.redis.RedisService
import bot.wuliang.translation.PublicExportService
import bot.wuliang.utils.ParseDataUtil
import kotlinx.coroutines.runBlocking
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class WarframeDataService {
    @Autowired
    private lateinit var translations: PublicExportService

    @Autowired
    private lateinit var redisService: RedisService

    @Autowired
    private lateinit var parseDataUtil: ParseDataUtil

    /**
     * 优先读取当前翻译版本的裂缝缓存；未命中时拉取世界状态并重新解析、缓存。
     */
    fun getFissuresData(): List<Fissure?>? {
        if (!redisService.hasKey(translations.cacheKey(WF_FISSURE_KEY))) {
            val data = HttpUtil.doGetJson(WARFRAME_STATUS_URL)
            runBlocking {
                return@runBlocking parseDataUtil.parseFissure(data["ActiveMissions"], data["VoidStorms"])
            }
        }
        return redisService.getValueTyped(translations.cacheKey(WF_FISSURE_KEY))
    }
}