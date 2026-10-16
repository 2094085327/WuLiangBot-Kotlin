package bot.wuliang.worldstate.parser

import com.fasterxml.jackson.databind.JsonNode

data class NightwaveChallengeState(
    val id: String, val key: String, val daily: Boolean, val elite: Boolean, val permanent: Boolean,
    val activation: Long?, val expiry: Long?,
) {
    val reputation: Int get() = if (daily) 1000 else if (elite) 7000 else 4500
}

data class NightwaveState(
    val activation: Long?, val expiry: Long?, val tag: String, val season: Int, val phase: Int, val params: String,
    val possibleChallenges: List<NightwaveChallengeState>?, val activeChallenges: List<NightwaveChallengeState>,
)

/** 电波协议解析；挑战是否每日、精英及其声望值在两种展示路径间保持一致。 */
object NightwaveParser {
    /**
     * 解析赛季信息及候选挑战，只将 [now] 时刻有效的挑战加入当前任务列表。
     *
     * @param season 官方 SeasonInfo 对象。
     * @param now 本次查询的 Unix 毫秒时间。
     * @return 保留官方标识、任务类别和时间边界的电波状态。
     */
    fun parse(season: JsonNode, now: Long): NightwaveState = NightwaveState(
        season.timestamp("Activation"), season.timestamp("Expiry"), season.text("AffiliationTag"),
        season.path("Season").asInt(), season.path("Phase").asInt(), season.text("Params"),
        season.get("Challenges")?.arrayItems()?.map(::challenge),
        season.path("ActiveChallenges").activeRecords(now).map(::challenge),
    )

    /** 读取单个挑战的官方标识、每日与精英标记、永久标记及时间边界。 */
    private fun challenge(record: JsonNode) = NightwaveChallengeState(
        record.recordId(), record.text("Challenge"), record.path("Daily").asBoolean(),
        record.text("Challenge").contains("hard", ignoreCase = true), record.path("Permanent").asBoolean(),
        record.timestamp("Activation"), record.timestamp("Expiry"),
    )
}