package bot.wuliang.worldstate.presentation

import bot.wuliang.translation.WorldStateCatalog
import bot.wuliang.worldstate.model.WorldEntry
import bot.wuliang.worldstate.model.WorldSection
import bot.wuliang.worldstate.parser.WeeklyParser
import com.fasterxml.jackson.databind.JsonNode
import java.time.LocalDate

/** 周常面板展示全部科研难度与日历事件；官方当前轮换与本地下一轮预测分开处理。 */
internal class WeeklySections(private val display: WorldStateDisplay) {
    /** 组装官方回廊、全部科研难度和当前日历事件；所有板块共用 [now] 查询时刻。 */
    fun weekly(root: JsonNode, now: Long): WorldSection {
        val entries = mutableListOf<WorldEntry>()
        WeeklyParser.circuit(root.path("EndlessXpSchedule"), now)?.let { circuit ->
            entries.addAll(circuit.choices.map { choice ->
                WorldEntry(
                    choice.category, if (choice.category == "EXC_HARD") "钢铁之路回廊" else "普通回廊", "回廊",
                    details = choice.choices.map { key ->
                        display.name(
                            if (choice.category == "EXC_HARD") display.index.incarnonAdapter(key) ?: key else key
                        )
                    },
                    activation = circuit.activation, expiry = circuit.expiry,
                )
            })
        }
        WeeklyParser.conquests(root.path("Conquests"), now).forEach { conquest ->
            entries.addAll(conquest.missions.mapIndexed { index, mission ->
                WorldEntry(
                    "${conquest.type}-$index", display.name(mission.missionType.orEmpty()),
                    if (conquest.type == "CT_HEX") "时光科研" else "深层科研", display.name(mission.faction.orEmpty()),
                    details = mission.difficulties.flatMap { difficulty ->
                        listOf(
                            "${if (difficulty.type == "CD_HARD") "精英" else "普通"}：${
                                display.conquestLookup(
                                    difficulty.deviation.orEmpty()
                                )
                            }"
                        ) +
                                difficulty.risks.map(display.conquestLookup)
                    },
                    activation = conquest.activation, expiry = conquest.expiry,
                )
            })
        }
        WeeklyParser.calendar(root.path("KnownCalendarSeasons"), now)?.let { season ->
            entries.addAll(season.days.map { day ->
                val calendarDate = runCatching {
                    LocalDate.ofYearDay(1999, day.day).let { "${it.monthValue} 月 ${it.dayOfMonth} 日" }
                }.getOrDefault("第 ${day.day} 天")
                WorldEntry(
                    "calendar-${day.day}", "1999 日历 · $calendarDate", "1999 日历",
                    details = day.events.flatMap { event ->
                        listOfNotNull(
                            event.challenge?.let(display::description),
                            event.reward?.let(display::name),
                            event.upgrade?.let(display.calendarLookup),
                            event.character?.let { "生日：${display.name(it)}" })
                    } + listOfNotNull(WorldStateCatalog.birthdays[day.day]?.let { "生日：${display.name(it)}" }),
                    activation = season.activation, expiry = season.expiry,
                )
            })
        }
        return WorldSection("weekly", "周常", entries)
    }
}
