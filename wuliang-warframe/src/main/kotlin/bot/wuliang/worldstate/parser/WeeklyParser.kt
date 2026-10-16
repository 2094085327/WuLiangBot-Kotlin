package bot.wuliang.worldstate.parser

import com.fasterxml.jackson.databind.JsonNode

data class ConquestDifficultyState(val type: String, val deviation: String?, val risks: List<String>)
data class ConquestMissionState(
    val missionType: String?,
    val faction: String?,
    val difficulties: List<ConquestDifficultyState>
)

data class ConquestState(
    val type: String?, val activation: Long?, val expiry: Long?,
    val missions: List<ConquestMissionState>, val variables: List<String>,
)

data class CalendarEventState(val challenge: String?, val reward: String?, val upgrade: String?, val character: String?)
data class CalendarDayState(val day: Int, val events: List<CalendarEventState>)
data class CalendarState(
    val season: String?,
    val activation: Long?,
    val expiry: Long?,
    val days: List<CalendarDayState>
)

data class CircuitChoiceState(val category: String, val choices: List<String>)
data class CircuitState(val activation: Long?, val expiry: Long?, val choices: List<CircuitChoiceState>)

/** 周常协议解析保留所有难度与事件；各输出端可以选择展示子集，不能各自重解释协议。 */
object WeeklyParser {
    /** 解析 [now] 时刻有效的科研任务，保留全部难度、风险与额外变量供展示端选择。 */
    fun conquests(records: JsonNode, now: Long): List<ConquestState> = records.activeRecords(now).map { record ->
        ConquestState(
            record.get("Type")?.asText(), record.timestamp("Activation"), record.timestamp("Expiry"),
            record.items("Missions").map { mission ->
                ConquestMissionState(
                    mission.get("missionType")?.asText(), mission.get("faction")?.asText(),
                    mission.items("difficulties").map { difficulty ->
                        ConquestDifficultyState(
                            difficulty.text("type"), difficulty.get("deviation")?.asText(),
                            difficulty.items("risks").map { it.asText() })
                    },
                )
            },
            record.items("Variables").map { it.asText() },
        )
    }

    /** 日历也是单轮任务，跨季发布重叠时与突击一致，选最新的有效记录。 */
    fun calendar(records: JsonNode, now: Long): CalendarState? = records.latestActive(now)?.let { record ->
        CalendarState(
            record.get("Season")?.asText(), record.timestamp("Activation"), record.timestamp("Expiry"),
            record.items("Days").mapNotNull { day ->
                val number = day.get("day")?.asInt() ?: return@mapNotNull null
                CalendarDayState(number, day.items("events").map { event ->
                    CalendarEventState(
                        event.get("challenge")?.asText(), event.get("reward")?.asText(),
                        event.get("upgrade")?.asText(), event.get("character")?.asText()
                    )
                })
            },
        )
    }

    /** 选择开始时间最新的有效官方回廊记录，保留普通和钢铁之路候选标识；无有效记录时返回 null。 */
    fun circuit(records: JsonNode, now: Long): CircuitState? = records.latestActive(now)?.let { record ->
        CircuitState(record.timestamp("Activation"), record.timestamp("Expiry"), record.items("CategoryChoices").map {
            CircuitChoiceState(it.text("Category"), it.items("Choices").map { choice -> choice.asText() })
        })
    }
}