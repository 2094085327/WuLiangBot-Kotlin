package bot.wuliang.worldstate.service

import bot.wuliang.config.WARFRAME_SPIRAL_SCHEDULE
import bot.wuliang.config.WfMarketConfig.WF_MOODSPIRALS_KEY
import bot.wuliang.entity.vo.WfUtilVo
import bot.wuliang.moudles.MoodSpirals
import bot.wuliang.redis.RedisService
import bot.wuliang.translation.PublicExportService
import bot.wuliang.utils.TimeUtils
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.stereotype.Service
import java.io.File
import java.time.Duration
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

/** 根据本地双衍配置生成详细轮换，保留地点、角色和伤害类型等补充信息。 */
@Service
class MoodSpiralService(
    private val redisService: RedisService,
    private val translations: PublicExportService,
) {
    private val dateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME

    /** 按系统默认时区为本地时间补上偏移量，输出 ISO 偏移日期时间字符串。 */
    private fun formatDateTime(dateTime: LocalDateTime): String {
        val zonedDateTime = dateTime.atZone(ZoneId.systemDefault()) // 转换为 ZonedDateTime
        return zonedDateTime.toOffsetDateTime().format(dateTimeFormatter) // 转换为 OffsetDateTime 并格式化
    }

    /** 查找开始时间早于 [currentTime] 的最新双衍记录；无匹配时返回 null。 */
    fun findSpiralsCurrentTime(
        wfWeathers: List<WfUtilVo.WfWeather>,
        currentTime: LocalDateTime
    ): WfUtilVo.WfWeather? {
        return wfWeathers
            .filter { OffsetDateTime.parse(it.startTime, dateTimeFormatter).toLocalDateTime().isBefore(currentTime) }
            .maxByOrNull { OffsetDateTime.parse(it.startTime, dateTimeFormatter).toLocalDateTime() }
    }

    /** 返回轮换列表中最晚的开始时间；列表为空时返回 [LocalDateTime.MIN]。 */
    fun findLatestWeatherAndIndex(weathers: List<WfUtilVo.WfWeather>): LocalDateTime {
        return weathers.maxOfOrNull { OffsetDateTime.parse(it.startTime, dateTimeFormatter).toLocalDateTime() }
            ?: LocalDateTime.MIN
    }

    /** 按 [currentTime] 推进轮换时间并更新 [spiralData]，返回同一个配置对象。 */
    fun updateWeathers(spiralData: WfUtilVo.SpiralsData, currentTime: LocalDateTime): WfUtilVo.SpiralsData {
        val maxStartTime = findLatestWeatherAndIndex(spiralData.wfWeather)
        spiralData.wfWeather = updateWeatherStartTimes(spiralData.wfWeather, maxStartTime, currentTime)
        return spiralData
    }

    /**
     * 根据最近开始时间和两小时间隔推进排期，原地更新各记录的开始时间。
     *
     * @param weatherData 需要推进的轮换记录。
     * @param maxTime 最近的开始时间，为 null 时直接返回原列表。
     * @param currentTime 本次计算使用的本地时间。
     */
    fun updateWeatherStartTimes(
        weatherData: List<WfUtilVo.WfWeather>,
        maxTime: LocalDateTime?,
        currentTime: LocalDateTime
    ): List<WfUtilVo.WfWeather> {
        if (maxTime == null) return weatherData

        // hours <= 1 说明仍在当前轮次内，不需要更新
        val hours = Duration.between(maxTime, currentTime).toHours()
        if (hours <= 1) return weatherData

        val weatherCount = weatherData.size
        // 当前时间所在位置下标
        val nowTimeIndex = (hours / 2 - 1) % weatherCount

        return weatherData.mapIndexed { i, weather ->
            // 计算时间偏移量，i - nowTimeIndex 可以是负值，偏移量是相对的
            val offset = (i - nowTimeIndex) * 2L
            weather.startTime = formatDateTime(maxTime.plusHours((if (hours % 2 != 0L) hours - 1 else hours) + offset))
            weather
        }
    }

    /**
     * 按 [stateId] 分组角色并翻译角色与地点。
     *
     * @return 可出现角色与被排除角色的列表，顺序为可出现、被排除。
     */
    fun getNpcLists(
        weatherData: WfUtilVo.SpiralsData,
        stateId: Int
    ): Pair<MutableList<Map<String, String>>, MutableList<Map<String, String>>> {
        val npcList = mutableListOf<Map<String, String>>()
        val excludeNpcList = mutableListOf<Map<String, String>>()

        weatherData.places.forEach { place ->
            place.npc?.forEach { npc ->
                val label = mapOf(translations.spiralName(npc.name) to translations.spiralName(place.name))
                if (npc.excludeIds.contains(stateId)) excludeNpcList.add(label)
                else npcList.add(label)
            }
        }

        return Pair(npcList, excludeNpcList)
    }

    /**
     * 按 [stateId] 分组并翻译地点。
     *
     * @return 被排除地点与可出现地点的列表，顺序为被排除、可出现。
     */
    fun getPlaceLists(weatherData: WfUtilVo.SpiralsData, stateId: Int): Pair<MutableList<String>, MutableList<String>> {
        val excludePlaceList = mutableListOf<String>()
        val noExcludePlaceList = mutableListOf<String>()

        weatherData.excludePlaces.forEach { place ->
            val label = translations.spiralName(place.name)
            if (place.excludeIds.contains(stateId)) excludePlaceList.add(label)
            else noExcludePlaceList.add(label)
        }

        return Pair(excludePlaceList, noExcludePlaceList)
    }

    /** 优先读取详细双衍缓存，未命中时加载本地配置并缓存至下一轮；无当前或下一轮记录时返回 null。 */
    fun getMoodSpirals(): MoodSpirals? {
        val cacheKey = translations.cacheKey(WF_MOODSPIRALS_KEY)
        val (ttl, cached) = redisService.getExpireAndValueTyped<MoodSpirals>(cacheKey)
        if (cached != null) return cached.also { it.remainTime = TimeUtils.formatTimeBySecond(ttl ?: 0L) }
        val data = jacksonObjectMapper().readValue(File(WARFRAME_SPIRAL_SCHEDULE), WfUtilVo.SpiralsData::class.java)
        val now = LocalDateTime.now(ZoneId.of("Asia/Shanghai"))
        val current = findSpiralsCurrentTime(updateWeathers(data, now).wfWeather, now) ?: return null
        val nextTime = OffsetDateTime.parse(current.startTime).toLocalDateTime().plusHours(2)
        val next =
            findSpiralsCurrentTime(updateWeathers(data, nextTime.plusMinutes(1)).wfWeather, nextTime.plusMinutes(1))
                ?: return null
        val seconds = Duration.between(now, nextTime).seconds.coerceAtLeast(1)
        val (npc, excludedNpc) = getNpcLists(data, current.stateId)
        val (excludedPlaces, places) = getPlaceLists(data, current.stateId)
        return MoodSpirals(
            currentState = data.weatherStates[current.stateId]?.let(translations::spiralName),
            nextState = data.weatherStates[next.stateId]?.let(translations::spiralName),
            damageType = data.damageTypes[current.dmgStateId]?.let(translations::spiralName),
            damageTypeKey = data.damageTypes[current.dmgStateId]?.lowercase(java.util.Locale.ROOT),
            npc = npc, excludeNpc = excludedNpc, excludePlace = excludedPlaces, noExcludePlace = places,
            remainTime = TimeUtils.formatTimeBySecond(seconds),
            nextExcludePlace = data.excludePlaces.filter { next.stateId in it.excludeIds }
                .map { translations.spiralName(it.name) }
        ).also { redisService.setValueWithExpiry(cacheKey, it, seconds, TimeUnit.SECONDS) }
    }
}