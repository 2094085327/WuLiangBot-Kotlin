package bot.wuliang.api

import bot.wuliang.config.WfMarketConfig.WF_CALENDAR_KEY
import bot.wuliang.config.WfMarketConfig.WF_CONQUEST_KEY
import bot.wuliang.exception.RespBean
import bot.wuliang.moudles.CalendarSeason
import bot.wuliang.moudles.Conquest
import bot.wuliang.redis.RedisService
import bot.wuliang.translation.PublicExportService
import bot.wuliang.utils.TimeUtils.formatDuration
import bot.wuliang.utils.TimeUtils.getInstantNow
import bot.wuliang.worldstate.service.WeeklyQueryService
import bot.wuliang.worldstate.source.WorldStateSource
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Duration

/** 科研与日历页面接口，底层协议解析与后台世界状态面板共用。 */
@Api(tags = ["Warframe接口"])
@RestController
@RequestMapping("/warframe")
class WarframeWeeklyController(
    private val weeklyQueryService: WeeklyQueryService,
    private val worldStateSource: WorldStateSource,
    private val translations: PublicExportService,
    private val redisService: RedisService,
) {

    /** 读取科研任务缓存；缺失或已到轮换边界时刷新任务，并更新剩余时间。 */
    @ApiOperation("科研任务信息")
    @GetMapping("/conquest")
    fun conquest(): RespBean<out List<Conquest>> {
        if (!redisService.hasKey(translations.cacheKey(WF_CONQUEST_KEY))) {
            val data = worldStateSource.get()
            weeklyQueryService.refreshConquests(data["Conquests"])
        }
        val conquestList = redisService.getValueTyped<List<Conquest>>(translations.cacheKey(WF_CONQUEST_KEY))
            ?: return RespBean.error()

        val result = conquestList
            .onEach { conquest ->
                conquest.eta = formatDuration(Duration.between(getInstantNow(), conquest.expiry))
            }

        return RespBean.success(result)
    }

    /** 读取当前 1999 日历季节及事件，并按季节结束时间更新倒计时。 */
    @ApiOperation("1999 日历信息")
    @GetMapping("/calendar")
    fun calendar(): RespBean<out CalendarSeason> {
        if (!redisService.hasKey(translations.cacheKey(WF_CALENDAR_KEY))) {
            val data = worldStateSource.get()
            weeklyQueryService.getCalendar(data["KnownCalendarSeasons"])
        }
        val calendarSeason = redisService.getValueTyped<CalendarSeason>(translations.cacheKey(WF_CALENDAR_KEY))
            ?: return RespBean.error()

        calendarSeason.eta = formatDuration(Duration.between(getInstantNow(), calendarSeason.expiry))

        return RespBean.success(calendarSeason)
    }
}