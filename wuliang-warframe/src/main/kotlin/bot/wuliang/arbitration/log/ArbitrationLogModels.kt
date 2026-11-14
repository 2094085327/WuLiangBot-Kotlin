package bot.wuliang.arbitration.log

data class ArbitrationLogOptions(
    val run: Int = 1,
    val dropChance: Boolean = true,
    val resource: Boolean = true,
    val blessing: Boolean = true,
    val retriever: Boolean = true,
    val details: Boolean = false,
    val previous: Boolean = false,
    val recordId: Long? = null,
) {
    val multiplier: Double
        get() = (if (dropChance) 2.0 else 1.0) * (if (resource) 2.0 else 1.0) *
                (if (blessing) 1.25 else 1.0) * (if (retriever) 1.18 else 1.0)
}

data class ArbitrationLogPhase(
    val index: Int,
    val kind: String,
    val drones: Int,
    val enemies: Int?,
    val durationSeconds: Double?,
    val completed: Boolean,
    val startSeconds: Double,
)

data class ArbitrationLogLivingSample(val time: Double, val alive: Int)
data class ArbitrationLogDroneBurst(val time: Double, val count: Int)

data class ArbitrationLogRun(
    val nodeId: String,
    val missionKind: String,
    val drones: Int,
    val enemies: Int?,
    val hostDurationSeconds: Double?,
    val waves: Int?,
    val rounds: Int?,
    val phases: List<ArbitrationLogPhase>,
    val completed: Boolean,
    val playedAt: Long?,
    val warnings: List<String>,
    val livingSamples: List<ArbitrationLogLivingSample>,
    val droneBursts: List<ArbitrationLogDroneBurst>,
    val phaseBoundaries: List<Double>,
    val detailsTruncated: Boolean,
    val startedAt: Long? = null,
    val startSeconds: Double = 0.0,
    val observedDurationSeconds: Double = 0.0,
    val gameBuild: String? = null,
    val clientType: String? = null,
    val sessionHash: String?,
)

data class ArbitrationLogParseResult(
    val runs: List<ArbitrationLogRun>,
    val validTotal: Int,
    val excludedShort: Int,
    val excludedNonHost: Int,
    val warnings: List<String>,
    val inputIncomplete: Boolean = false,
)

data class ArbitrationLogRow(
    val label: String,
    val drones: Int,
    val cumulativeDrones: Int,
    val enemies: Int?,
    val durationSeconds: Double?,
    val expectedVitus: Double,
    val completed: Boolean,
)

data class ArbitrationLogReport(
    val nodeId: String,
    val node: String,
    val missionType: String,
    val faction: String,
    val completed: Boolean,
    val playedAt: Long?,
    val run: Int,
    val totalRuns: Int,
    val drones: Int,
    val enemies: Int?,
    val waves: Int?,
    val rounds: Int?,
    val durationSeconds: Double?,
    val dronesPerMinute: Double?,
    val enemiesPerMinute: Double?,
    val multiplier: Double,
    val bonuses: List<String>,
    val expectedFromDrones: Double,
    val expectedFromRounds: Double?,
    val expectedVitus: Double?,
    val vitusPerHour: Double?,
    val fullBonusVitusPerHour: Double?,
    val grade: String?,
    val rows: List<ArbitrationLogRow>,
    val detailKind: String,
    val details: ArbitrationLogDetailReport?,
    val warnings: List<String>,
    val recordId: String? = null,
)

/** 此评级与排期页的节点基准评级独立。 */
object ArbitrationLogMath {
    const val DROP_CHANCE = 0.06
    const val FULL_MULTIPLIER = 5.9

    fun roundsExpected(rounds: Int): Double = rounds +
            3.0 * (0.07 * minOf(rounds, 4) + 0.10 * maxOf(0, rounds - 4))

    fun grade(perHour: Double): String = when {
        perHour >= 800 -> "S"
        perHour >= 700 -> "A+"
        perHour >= 600 -> "A"
        perHour >= 500 -> "A-"
        else -> "F"
    }
}