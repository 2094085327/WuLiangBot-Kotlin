package bot.wuliang.worldstate.presentation

import bot.wuliang.config.WfMarketConfig
import bot.wuliang.config.WfMarketConfig.WF_VOIDTRADER_KEY
import bot.wuliang.imageProcess.WebImgUtil
import bot.wuliang.moudles.Conquest
import bot.wuliang.moudles.NightWave
import bot.wuliang.moudles.Sortie
import bot.wuliang.moudles.VoidTrader
import bot.wuliang.redis.RedisService
import bot.wuliang.tencentCos.CosFileServiceImpl
import bot.wuliang.translation.PublicExportService
import bot.wuliang.utils.TimeUtils
import bot.wuliang.worldstate.service.MissionQueryService
import bot.wuliang.worldstate.service.WeeklyQueryService
import bot.wuliang.worldstate.source.WorldStateSource
import com.fasterxml.jackson.databind.JsonNode
import org.springframework.stereotype.Service
import java.time.ZoneId

/** 预加载本周数据并生成周常图片；不完整的新轮换不能写入整周图片缓存。 */
@Service
class WeeklyImageService(
    private val worldStateSource: WorldStateSource,
    private val translations: PublicExportService,
    private val redisService: RedisService,
    private val missionQueryService: MissionQueryService,
    private val weeklyQueryService: WeeklyQueryService,
    private val txCosService: CosFileServiceImpl,
    private val webImgUtil: WebImgUtil,
) {

    /** 定时任务传入快照时刷新数据；普通查询允许复用已经齐全的本周缓存。 */
    fun imageUrl(worldState: JsonNode? = null): String {
        // 手动查询与定时任务共用预加载入口，避免依赖其他指令曾经填充过缓存。
        val archonKey = translations.cacheKey(WfMarketConfig.WF_ARCHONHUNT_KEY)
        val nightWaveKey = translations.cacheKey(WfMarketConfig.WF_NIGHTWAVE_KEY)
        val conquestKey = translations.cacheKey(WfMarketConfig.WF_CONQUEST_KEY)
        val cachedArchon = redisService.getValueTyped<Sortie>(archonKey)
        val cachedNightWave = redisService.getValueTyped<NightWave>(nightWaveKey)
        val cachedConquests = redisService.getValueTyped<List<Conquest>>(conquestKey)
        // 定时任务显式传入新快照时刷新；普通查询在缓存齐全时直接复用。
        val data = worldState ?: if (cachedArchon == null || cachedNightWave == null || cachedConquests == null) {
            worldStateSource.get()
        } else null
        val archon = if (data != null) missionQueryService.refreshArchonHunt(data.path("LiteSorties")) else cachedArchon
        val nightWave =
            if (data != null) missionQueryService.refreshNightwave(data.path("SeasonInfo")) else cachedNightWave
        val conquests =
            if (data != null) weeklyQueryService.refreshConquests(data.path("Conquests")) else cachedConquests
        // 刷新瞬间上游可能还没发布新一期，不能把缺失状态缓存为整周图片。
        check(archon != null) { "本周执刑官数据尚未就绪，请稍后重试" }
        check(nightWave?.activeChallenges?.any { it.isDaily != true } == true) {
            "本周午夜电波数据尚未就绪，请稍后重试"
        }
        check(conquests.orEmpty().map { it.type }.containsAll(listOf("CT_LAB", "CT_HEX"))) {
            "本周科研数据尚未就绪，请稍后重试"
        }
        // 周常每周一刷新，按周一日期与奸商状态生成图片名，缓存在COS中一周
        val weeklyKey = TimeUtils.getFirstDayOfWeek()
            .atZone(ZoneId.of("UTC"))
            .toLocalDate()
            .toString()
        // 奸商激活状态写入缓存key，抵达后第一次生成周常图会带上库存
        val voidTraderList = redisService.getValueTyped<List<VoidTrader>>(translations.cacheKey(WF_VOIDTRADER_KEY))
        val voidTraderState = when {
            voidTraderList?.any { it.isActive == true } == true -> "active"
            voidTraderList.isNullOrEmpty() -> "none"
            else -> "upcoming"
        }
        txCosService.ensureLifecycleRule("weekly-", 7)

        val imgData = WebImgUtil.ImgData(
            url = "http://${webImgUtil.frontendAddress}/weekly",
            imgName = "weekly-v2-$weeklyKey-$voidTraderState-${translations.cacheTag()}",
            element = "#app",
            waitElement = ".warframeWeekly"
        )

        return webImgUtil.getImgUrl(imgData)
    }
}