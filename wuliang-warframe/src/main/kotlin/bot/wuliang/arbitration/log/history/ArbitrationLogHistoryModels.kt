package bot.wuliang.arbitration.log.history

/** 历史列表读取标量统计。 */
data class ArbitrationLogHistoryRecord(
    var recordId: Long? = null,
    var nodeId: String = "",
    var missionKind: String = "",
    var clientType: String? = null,
    var startedAt: Long? = null,
    var durationSeconds: Double? = null,
    var drones: Int = 0,
    var enemies: Int? = null,
    var completed: Boolean = false,
    var hasConflict: Boolean = false,
    var fullBonusVitusPerHour: Double? = null,
)

data class ArbitrationLogHistoryItem(
    val recordId: String,
    val startedAt: Long?,
    val node: String,
    val missionType: String,
    val clientType: String?,
    val durationSeconds: Double?,
    val proficiencyPercent: Double?,
    val vitusPerHour: Double?,
    val grade: String?,
    val completed: Boolean,
    val hasConflict: Boolean,
)

data class ArbitrationLogHistoryReport(
    val page: Int,
    val pageSize: Int,
    val total: Long,
    val totalPages: Long,
    val items: List<ArbitrationLogHistoryItem>,
)