package bot.wuliang.arbitration

data class ArbitrationMission(
    val nodeId: String,
    val node: String,
    val missionType: String,
    val faction: String,
    val tier: String,
    val activation: Long,
    val expiry: Long,
    val eta: String,
)

data class ArbitrationReport(
    val mode: String,
    val generatedAt: Long,
    val current: ArbitrationMission?,
    val next: ArbitrationMission?,
    val nextHighValue: ArbitrationMission?,
    val items: List<ArbitrationMission> = emptyList(),
    val filter: String = "所有",
    val page: Int = 1,
    val pageSize: Int = 15,
    val total: Int = 0,
    val totalPages: Int = 0,
    val hasNext: Boolean = false,
    val scheduleThrough: Long,
    val stale: Boolean,
)

class ArbitrationUnavailableException(message: String) : RuntimeException(message)