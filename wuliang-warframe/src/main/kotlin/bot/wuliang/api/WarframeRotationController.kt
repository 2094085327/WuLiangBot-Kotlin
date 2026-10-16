package bot.wuliang.api

import bot.wuliang.config.WfMarketConfig.WF_INCARNON_KEY
import bot.wuliang.config.WfMarketConfig.WF_INCARNON_RIVEN_KEY
import bot.wuliang.config.WfMarketConfig.WF_SIMARIS_KEY
import bot.wuliang.exception.RespBean
import bot.wuliang.moudles.Incarnon
import bot.wuliang.moudles.MoodSpirals
import bot.wuliang.moudles.Simaris
import bot.wuliang.moudles.SteelPath
import bot.wuliang.redis.RedisService
import bot.wuliang.translation.PublicExportService
import bot.wuliang.utils.TimeUtils.formatDuration
import bot.wuliang.utils.TimeUtils.formatTimeBySecond
import bot.wuliang.utils.TimeUtils.getInstantNow
import bot.wuliang.utils.TimeUtils.getNextMonday
import bot.wuliang.worldstate.service.LocalRotationService
import bot.wuliang.worldstate.service.MoodSpiralService
import bot.wuliang.worldstate.source.WorldStateSource
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Duration
import java.time.Instant

/** 本地轮换与补充数据接口，保持原有页面响应结构。 */
@Api(tags = ["Warframe接口"])
@RestController
@RequestMapping("/warframe")
class WarframeRotationController(
    private val moodSpiralService: MoodSpiralService,
    private val localRotationService: LocalRotationService,
    private val worldStateSource: WorldStateSource,
    private val translations: PublicExportService,
    private val redisService: RedisService,
) {

    /** 读取钢铁之路奖励及缓存剩余时间，更新页面响应中的奖励倒计时。 */
    @ApiOperation("钢路奖励")
    @GetMapping("/steelPath")
    fun steelPath(): RespBean<SteelPath> {
        var (expiry, steelPathEntity) = localRotationService.getSteelPath()
        if (expiry == null) expiry = -1L
        steelPathEntity!!.eta = formatTimeBySecond(expiry)

        return RespBean.success(steelPathEntity)
    }

    /** 读取本地回廊轮换的本周、下周及紫卡价格数据，并刷新当前轮换倒计时。 */
    @ApiOperation("回廊信息")
    @GetMapping("/incarnon")
    fun incarnon(): RespBean<out Incarnon> {
        val incarnonEntity =
            if (!redisService.hasKey(translations.cacheKey(WF_INCARNON_KEY))) {
                localRotationService.getIncarnon()
            } else {
                val redisIncarnon = redisService.getValueTyped<Incarnon>(translations.cacheKey(WF_INCARNON_KEY))
                    ?: return RespBean.error()
                redisIncarnon.eta = formatTimeBySecond(Duration.between(Instant.now(), getNextMonday()).seconds)
                redisIncarnon
            }

        return RespBean.success(incarnonEntity)
    }

    /** 读取回廊武器对应的紫卡信息缓存；缓存不存在时返回 null。 */
    @RequestMapping("/incarnonRiven")
    @Suppress("UNCHECKED_CAST")
    fun incarnonRiven(): Map<String, String>? {
        return redisService.getValueTyped(translations.cacheKey(WF_INCARNON_RIVEN_KEY))
    }

    /** 读取详细双衍轮换，包括角色、地点和伤害类型；无有效轮换时返回错误响应。 */
    @ApiOperation("双衍平原信息")
    @GetMapping("/spirals")
    fun spirals(): RespBean<out MoodSpirals> {
        val moodSpiralsEntity = moodSpiralService.getMoodSpirals() ?: return RespBean.error()

        return RespBean.success(moodSpiralsEntity)
    }

    /** 读取今日结合仪式目标与地点，并按日更结束时间刷新倒计时。 */
    @ApiOperation("圣殿结合仪式目标信息")
    @GetMapping("/simaris")
    fun simaris(): RespBean<out Simaris> {
        if (!redisService.hasKey(translations.cacheKey(WF_SIMARIS_KEY))) {
            val data = worldStateSource.get()
            localRotationService.getSimaris(data["LibraryInfo"])
        }
        val simarisEntity = redisService.getValueTyped<Simaris>(translations.cacheKey(WF_SIMARIS_KEY))
            ?: return RespBean.error(message = "圣殿结合仪式目标没有找到~")

        simarisEntity.eta = formatDuration(Duration.between(getInstantNow(), simarisEntity.expiry))

        return RespBean.success(simarisEntity)
    }
}