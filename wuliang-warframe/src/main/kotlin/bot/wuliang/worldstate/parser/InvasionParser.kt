package bot.wuliang.worldstate.parser

import com.fasterxml.jackson.databind.JsonNode
import kotlin.math.abs

data class InvasionState(
    val id: String, val activation: Long?, val description: String, val faction: String, val defenderFaction: String,
    val node: String, val count: Int, val goal: Int, val progress: Double?, val remainingMillis: Long?,
    val attackerReward: RewardState, val defenderReward: RewardState,
) {
    val infestation: Boolean get() = faction == "FC_INFESTATION"
}

/** 共用入侵进度和剩余时间估算；now、activation 与 remainingMillis 的单位均为毫秒。 */
object InvasionParser {
    /**
     * 过滤已结束的入侵，计算共用进度、奖励和剩余时间估算。
     *
     * @param records 官方 Invasions 数组。
     * @param now 本次查询的 Unix 毫秒时间。
     * @return 未结束的入侵；目标数量无效时进度为空，无法估算时剩余时间为空。
     */
    fun parse(records: JsonNode, now: Long): List<InvasionState> = records.arrayItems()
        .filterNot { it.path("Completed").asBoolean() }.map { record ->
            val count = record.path("Count").asInt()
            val goal = record.path("Goal").asInt()
            val activation = record.timestamp("Activation")
            val completed = abs(count.toLong())
            val remaining = if (goal > 0 && completed > 0 && activation != null) {
                (goal.toLong() - completed).coerceAtLeast(0) * ((now - activation).coerceAtLeast(0) / completed)
            } else null
            // 感染入侵从 100% 开始，阵营入侵从 50% 开始；无有效目标时不伪造进度。
            val progress = if (goal > 0) {
                val fraction = 1 + count.toDouble() / goal
                (fraction * if (record.text("Faction") == "FC_INFESTATION") 100 else 50).coerceIn(0.0, 100.0)
            } else null
            InvasionState(
                record.recordId(),
                activation,
                record.text("LocTag"),
                record.text("Faction"),
                record.text("DefenderFaction"),
                record.text("Node"),
                count,
                goal,
                progress,
                remaining,
                RewardParser.parse(record.path("AttackerReward")),
                RewardParser.parse(record.path("DefenderReward")),
            )
        }
}