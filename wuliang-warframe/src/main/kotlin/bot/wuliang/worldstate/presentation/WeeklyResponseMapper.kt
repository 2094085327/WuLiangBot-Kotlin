package bot.wuliang.worldstate.presentation

import bot.wuliang.moudles.*
import bot.wuliang.translation.PublicExportService
import bot.wuliang.translation.WorldStateCatalog
import bot.wuliang.worldstate.parser.CalendarState
import bot.wuliang.worldstate.parser.ConquestState
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** 保留原有周常页面响应；科研页面只展示精英难度，面板可展示全部难度。 */
@Component
class WeeklyResponseMapper(
    private val translations: PublicExportService,
    private val catalog: WorldStateCatalog,
) {
    /** 将共用科研任务转换为原有页面响应，只展示精英难度并补充专项词库说明。 */
    fun conquest(value: ConquestState, now: Long): Conquest = Conquest(
        activation = value.activation.asInstant(), expiry = value.expiry.asInstant(),
        eta = remainingTime(value.expiry, now)?.replace("\\s+".toRegex(), ""), type = value.type,
        missions = value.missions.map { mission ->
            ConquestMission(
                faction = mission.faction?.let(catalog::faction),
                missionType = mission.missionType?.let(translations::name),
                difficulty = mission.difficulties.find { it.type == "CD_HARD" }?.let { difficulty ->
                    ConquestDifficulty(
                        difficulty.deviation?.let(translations::conquestInfo),
                        difficulty.risks.map(translations::conquestInfo)
                    )
                },
            )
        },
        variables = value.variables.map(translations::conquestInfo),
    )

    /** 将当前季节转换为日历页面响应，保留生日卡片替代任务列表的显示约定。 */
    fun calendar(value: CalendarState, now: Long): CalendarSeason = CalendarSeason(
        activation = value.activation.asInstant(),
        expiry = value.expiry.asInstant(),
        eta = remainingTime(value.expiry, now)?.replace("\\s+".toRegex(), ""),
        season = value.season,
        seasonKey = value.season,
        days = value.days.map { day ->
            val birthday = WorldStateCatalog.birthdays[day.day]?.let(translations::name)
            // 原有页面用生日卡片替代当日任务列表，保持该显示约定；解析结果仍保留全部事件。
            val event = day.events.firstOrNull()
            val type = when {
                birthday != null -> null
                event?.challenge != null -> "CET_CHALLENGE"
                event?.reward != null -> "CET_REWARD"
                event?.upgrade != null -> "CET_UPGRADE"
                else -> null
            }
            CalendarDay(
                day = day.day, date = formatCalendarDay(day.day), type = type, typeKey = type, birthday = birthday,
                items = if (birthday != null) emptyList() else day.events.mapNotNull { item ->
                    when {
                        item.challenge != null -> translations.info(item.challenge)
                        item.reward != null -> translations.info(item.reward)
                        item.upgrade != null -> translations.calendarUpgradeInfo(item.upgrade)
                        else -> null
                    }
                },
            )
        },
    )
}

/** 1999 是非闰年；协议中的年日统一以同一年解释。 */
internal fun formatCalendarDay(day: Int): String = runCatching {
    LocalDate.ofYearDay(1999, day).format(DateTimeFormatter.ofPattern("MM月dd日"))
}.getOrDefault("")