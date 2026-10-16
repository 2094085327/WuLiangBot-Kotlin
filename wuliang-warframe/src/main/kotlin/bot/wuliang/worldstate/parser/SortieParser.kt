package bot.wuliang.worldstate.parser

import com.fasterxml.jackson.databind.JsonNode

data class SortieMissionState(val node: String, val missionType: String?, val modifier: String?)

data class SortieState(
    val id: String, val boss: String, val activation: Long, val expiry: Long,
    val missions: List<SortieMissionState>,
)

/** 每日突击与执刑官猎杀共享任务结构，差异仅为任务数组的协议字段。 */
object SortieParser {
    /**
     * 选择开始时间最新的有效突击或执刑官记录，并读取对应任务数组。
     *
     * @param records 官方 Sorties 或 LiteSorties 数组。
     * @param missionField 每日突击使用 Variants，执刑官猎杀使用 Missions。
     * @param now 本次查询的 Unix 毫秒时间。
     * @return 当前任务；没有有效记录时返回 null。
     */
    fun parse(records: JsonNode, missionField: String, now: Long): SortieState? =
        records.latestActive(now)?.let { record ->
            SortieState(
                record.recordId(), record.text("Boss"), record.timestamp("Activation")!!, record.timestamp("Expiry")!!,
                record.items(missionField).map { mission ->
                    SortieMissionState(
                        mission.text("node"),
                        mission.get("missionType")?.asText(),
                        mission.get("modifierType")?.asText()
                    )
                },
            )
        }
}