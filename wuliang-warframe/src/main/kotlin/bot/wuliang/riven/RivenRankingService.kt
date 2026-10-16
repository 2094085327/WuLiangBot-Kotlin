package bot.wuliang.riven

import bot.wuliang.config.WARFRAME_WEEKLY_RIVEN_PC
import bot.wuliang.config.WfMarketConfig.WF_RIVEN_REROLLED_KEY
import bot.wuliang.config.WfMarketConfig.WF_RIVEN_UN_REROLLED_KEY
import bot.wuliang.httpUtil.HttpUtil
import bot.wuliang.jacksonUtil.JacksonUtil
import bot.wuliang.moudles.Riven
import bot.wuliang.redis.RedisService
import bot.wuliang.translation.PublicExportService
import bot.wuliang.utils.TimeUtils.getNextMonday
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

/** 同步官方紫卡周榜并维护已洗、未洗分组缓存，不参与实时世界状态解析。 */
@Service
class RivenRankingService(
    private val redisService: RedisService,
    private val translations: PublicExportService,
) {

    /** 同步官方紫卡周榜并翻译武器名称，分别缓存已洗与未洗分组至下周一；两个分组缓存均存在时直接复用。 */
    fun refreshRanking() {
        val unRerolledKey = translations.cacheKey(WF_RIVEN_UN_REROLLED_KEY)
        val rerolledKey = translations.cacheKey(WF_RIVEN_REROLLED_KEY)
        if (redisService.hasKey(unRerolledKey) && redisService.hasKey(rerolledKey)) return
        val data = HttpUtil.doGetStr(WARFRAME_WEEKLY_RIVEN_PC)
        val jsonData = JacksonUtil.readTree(JacksonUtil.convertSingleJsObjectToStandardJson(data))
        val rawRivenList = jsonData.map { eachRiven ->
            Riven(
                itemType = eachRiven["itemType"].textValue(),
                compatibility = eachRiven["compatibility"].textValue(),
                rerolled = eachRiven["rerolled"].booleanValue(),
                avg = eachRiven["avg"].doubleValue(),
                stddev = eachRiven["stddev"].doubleValue(),
                min = eachRiven["min"].intValue(),
                max = eachRiven["max"].intValue(),
                pop = eachRiven["pop"].intValue(),
                median = eachRiven["median"].doubleValue()
            )
        }

        val rivenList = rawRivenList.map { riven ->
            riven.copy(
                compatibilityId = riven.compatibility,
                compatibility = (riven.compatibility ?: riven.itemType)?.let(translations::name)
            )
        }
        // 根据rerolled字段将列表分成两个列表
        val rerolledList = rivenList.filter { it.rerolled == true }
        val unRerolledList = rivenList.filter { it.rerolled == false }

        val expire = Duration.between(Instant.now(), getNextMonday()).seconds

        redisService.setValueWithExpiry(unRerolledKey, unRerolledList, expire, TimeUnit.SECONDS)
        redisService.setValueWithExpiry(rerolledKey, rerolledList, expire, TimeUnit.SECONDS)
    }
}