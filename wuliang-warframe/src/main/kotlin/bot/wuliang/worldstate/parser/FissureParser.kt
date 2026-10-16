package bot.wuliang.worldstate.parser

import com.fasterxml.jackson.databind.JsonNode

/** 保留官方标识，名称翻译和倒计时由各输出端在查询时生成。 */
data class FissureState(
    val id: String, val node: String, val missionType: String?, val tier: String,
    val hard: Boolean, val storm: Boolean, val activation: Long, val expiry: Long,
)

/** 裂缝协议解析，返回有效任务的官方标识和 Unix 毫秒时间。 */
object FissureParser {
    /** 普通裂缝与九重天使用不同等级字段，但共享有效期和任务解析规则。 */
    fun parse(records: JsonNode, storm: Boolean, now: Long): List<FissureState> =
        records.activeRecords(now).map { record ->
            FissureState(
                record.recordId(), record.text("Node"), record.get("MissionType")?.asText(),
                record.text(if (storm) "ActiveMissionTier" else "Modifier"),
                record.path("Hard").asBoolean(), storm, record.timestamp("Activation")!!, record.timestamp("Expiry")!!,
            )
        }
}