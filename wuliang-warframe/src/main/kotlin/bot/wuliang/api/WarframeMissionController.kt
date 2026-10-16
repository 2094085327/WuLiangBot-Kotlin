package bot.wuliang.api

import bot.wuliang.config.WfMarketConfig.WF_ARCHONHUNT_KEY
import bot.wuliang.config.WfMarketConfig.WF_FISSURE_KEY
import bot.wuliang.config.WfMarketConfig.WF_INVASIONS_KEY
import bot.wuliang.config.WfMarketConfig.WF_NIGHTWAVE_KEY
import bot.wuliang.config.WfMarketConfig.WF_SORTIE_KEY
import bot.wuliang.exception.RespBean
import bot.wuliang.moudles.Fissure
import bot.wuliang.moudles.Invasions
import bot.wuliang.moudles.NightWave
import bot.wuliang.moudles.Sortie
import bot.wuliang.redis.RedisService
import bot.wuliang.translation.PublicExportService
import bot.wuliang.utils.TimeUtils.formatDuration
import bot.wuliang.utils.TimeUtils.formatTimeBySecond
import bot.wuliang.utils.TimeUtils.getInstantNow
import bot.wuliang.worldstate.service.MissionQueryService
import bot.wuliang.worldstate.source.WorldStateSource
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Duration

/** 共用数据解析与缓存服务。 */
@Api(tags = ["Warframe接口"])
@RestController
@RequestMapping("/warframe")
class WarframeMissionController(
    private val missionQueryService: MissionQueryService,
    private val worldStateSource: WorldStateSource,
    private val translations: PublicExportService,
    private val redisService: RedisService,
) {

    /** 读取当前执刑官猎杀；缓存缺失时刷新数据，并按缓存剩余秒数更新倒计时。 */
    @ApiOperation("执刑官数据")
    @GetMapping("/archonHunt")
    fun archonHunt(): RespBean<out Sortie> {
        if (!redisService.hasKey(translations.cacheKey(WF_ARCHONHUNT_KEY))) {
            missionQueryService.refreshArchonHunt(worldStateSource.get()["LiteSorties"])
        }
        var (expiry, archonHuntEntity) = redisService.getExpireAndValueTyped<Sortie>(
            translations.cacheKey(
                WF_ARCHONHUNT_KEY
            )
        )
        if (expiry == null) expiry = -1L
        // 更新时间为当前时间（秒）
        if (archonHuntEntity == null) return RespBean.error()
        archonHuntEntity.eta = formatTimeBySecond(expiry)

        return RespBean.success(archonHuntEntity)
    }

    /** 读取当前每日突击；缓存缺失时刷新数据，缺少有效任务时返回错误响应。 */
    @ApiOperation("每日突击数据")
    @GetMapping("/sortie")
    fun sortie(): RespBean<out Sortie> {
        if (!redisService.hasKey(translations.cacheKey(WF_SORTIE_KEY))) {
            missionQueryService.refreshSortie(worldStateSource.get()["Sorties"])
        }
        var (expiry, sortieEntity) = redisService.getExpireAndValueTyped<Sortie>(translations.cacheKey(WF_SORTIE_KEY))
        if (expiry == null) expiry = -1L
        // 更新时间为当前时间（秒）
        if (sortieEntity == null) return RespBean.error()
        sortieEntity.eta = formatTimeBySecond(expiry)

        return RespBean.success(sortieEntity)
    }

    /**
     * 按 [type] 筛选裂缝，并根据任务结束时间刷新返回结果的倒计时。
     *
     * @param type ordinary、hard 或 storm，分别表示普通、钢铁之路或九重天裂缝。
     * @return 筛选后的任务列表；类型无效或缓存不可用时返回错误响应。
     */
    @ApiOperation("裂缝信息")
    @GetMapping("/fissureList")
    suspend fun fissureList(@RequestParam("type") type: String): RespBean<out List<Fissure>> {
        if (!redisService.hasKey(translations.cacheKey(WF_FISSURE_KEY))) {
            val data = worldStateSource.get()
            missionQueryService.getFissures(data["ActiveMissions"], data["VoidStorms"])
        }
        val fissureList = redisService.getValueTyped<List<Fissure?>>(translations.cacheKey(WF_FISSURE_KEY))
            ?: return RespBean.error()

        val filterPredicate = when (type.lowercase()) {
            "ordinary" -> { fissure: Fissure -> fissure.storm != true && fissure.hard != true }
            "hard" -> { fissure: Fissure -> fissure.hard == true }
            "storm" -> { fissure: Fissure -> fissure.storm == true }
            else -> return RespBean.error()
        }

        val now = getInstantNow()
        val result = fissureList
            .filterNotNull()
            .filter(filterPredicate)
            .onEach { fissure ->
                fissure.eta = formatDuration(Duration.between(now, fissure.expiry))
            }

        return RespBean.toReturn(result.size, result)
    }

    /** 读取电波赛季与挑战，返回前根据赛季起止时间更新持续时间和剩余时间。 */
    @ApiOperation("电波信息")
    @GetMapping("/nightWave")
    fun nightWave(): RespBean<out NightWave> {
        if (!redisService.hasKey(translations.cacheKey(WF_NIGHTWAVE_KEY))) {
            missionQueryService.refreshNightwave(worldStateSource.get()["SeasonInfo"])
        }
        val nightWaveEntity =
            redisService.getValueTyped<NightWave>(translations.cacheKey(WF_NIGHTWAVE_KEY)) ?: return RespBean.error()
        nightWaveEntity.eta = formatTimeBySecond(Duration.between(getInstantNow(), nightWaveEntity.expiry).seconds)
        nightWaveEntity.startTime =
            formatTimeBySecond(Duration.between(nightWaveEntity.activation, getInstantNow()).seconds)

        return RespBean.success(nightWaveEntity)
    }

    /** 读取未结束的入侵列表；缓存缺失时使用共用入侵解析器重新生成响应。 */
    @ApiOperation("入侵信息")
    @GetMapping("/invasions")
    fun invasions(): RespBean<out List<Invasions>> {
        if (!redisService.hasKey(translations.cacheKey(WF_INVASIONS_KEY))) {
            val data = worldStateSource.get()
            missionQueryService.getInvasions(data["Invasions"])
        }

        val invasionsList = redisService.getValueTyped<List<Invasions>>(translations.cacheKey(WF_INVASIONS_KEY))
            ?: return RespBean.error()

        return RespBean.toReturn(invasionsList.size, invasionsList)
    }
}