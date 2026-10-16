package bot.wuliang.worldstate.service

import bot.wuliang.config.WfMarketConfig.WF_CALENDAR_KEY
import bot.wuliang.config.WfMarketConfig.WF_CONQUEST_KEY
import bot.wuliang.moudles.CalendarSeason
import bot.wuliang.moudles.Conquest
import bot.wuliang.redis.RedisService
import bot.wuliang.translation.PublicExportService
import bot.wuliang.worldstate.parser.WeeklyParser
import bot.wuliang.worldstate.parser.rotationCacheSeconds
import bot.wuliang.worldstate.presentation.WeeklyResponseMapper
import com.fasterxml.jackson.databind.JsonNode
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

/** 周常查询协调原有页面缓存，使用与后台面板相同的官方记录选择和字段解析。 */
@Service
class WeeklyQueryService(
    private val redis: RedisService,
    private val translations: PublicExportService,
    private val mapper: WeeklyResponseMapper,
) {
    /** 重建当前有效科研任务的页面响应，并将缓存保留至最近轮换边界。 */
    fun refreshConquests(records: JsonNode): List<Conquest> {
        translations.index()
        val now = System.currentTimeMillis()
        val values = WeeklyParser.conquests(records, now).map { mapper.conquest(it, now) }
        redis.setValueWithExpiry(
            translations.cacheKey(WF_CONQUEST_KEY),
            values,
            records.rotationCacheSeconds(now),
            TimeUnit.SECONDS
        )
        return values
    }

    /** 优先读取日历响应缓存，未命中时选择最新有效季节；无有效季节时清理旧缓存并返回 null。 */
    fun getCalendar(records: JsonNode): CalendarSeason? {
        translations.index()
        val cacheKey = translations.cacheKey(WF_CALENDAR_KEY)
        redis.getValueTyped<CalendarSeason>(cacheKey)?.let { return it }
        val now = System.currentTimeMillis()
        val season = WeeklyParser.calendar(records, now)
        if (season == null) {
            redis.deleteKey(cacheKey)
            return null
        }
        return mapper.calendar(season, now).also {
            redis.setValueWithExpiry(cacheKey, it, records.rotationCacheSeconds(now), TimeUnit.SECONDS)
        }
    }
}