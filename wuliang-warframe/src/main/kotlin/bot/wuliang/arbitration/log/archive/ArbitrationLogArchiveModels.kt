package bot.wuliang.arbitration.log.archive

import java.security.MessageDigest
import java.util.*

internal const val ARBITRATION_PARSER_REVISION = 2

/** 关联消息发送者 */
data class ArbitrationLogUploader(
    val platform: String,
    val botId: String,
    val messageType: String,
    val userId: String
) {
    fun key(): String {
        require(
            platform.isNotBlank() && platform.length <= 32 && botId.isNotBlank() && botId.length <= 128 &&
                    messageType.isNotBlank() && messageType.length <= 32 && userId.isNotBlank() && userId.length <= 128
        ) {
            "无法识别历史记录的发送者。"
        }
        return archiveHash(listOf(platform, botId, messageType, userId).joinToString("\u0000"))
    }
}

internal fun archiveHash(value: String): String = HexFormat.of().formatHex(
    MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
)

data class ArbitrationArchivedRun(
    val runKey: String,
    val factsHash: String,
    val nodeId: String,
    val missionKind: String,
    val gameBuild: String?,
    val clientType: String?,
    val startedAt: Long?,
    val endedAt: Long?,
    val durationSeconds: Double?,
    val observedSeconds: Double,
    val drones: Int,
    val enemies: Int?,
    val waves: Int?,
    val rounds: Int?,
    val completed: Boolean,
    val baselineEligible: Boolean,
    val qualityFlags: String,
    val dronesPerMinute: Double?,
    val enemiesPerMinute: Double?,
    val fullBonusVitus: Double?,
    val fullBonusVitusPerHour: Double?,
    val detailRank: Int,
    val structuredGzip: ByteArray,
    val parserRevision: Int = ARBITRATION_PARSER_REVISION,
)

/** 只读取去重和升级所需的小字段，不加载压缩详情。 */
data class ArbitrationArchiveState(
    var factsHash: String = "",
    var completed: Boolean = false,
    var observedSeconds: Double = 0.0,
    var detailRank: Int? = null,
)

/** 只保存最近文件的局序和解析摘要，不复制逐局采样。 */
data class ArbitrationArchiveManifest(
    val format: Int,
    val parserRevision: Int,
    val validTotal: Int,
    val excludedShort: Int,
    val excludedNonHost: Int,
    val warnings: List<String>,
    val inputIncomplete: Boolean,
    val runs: List<ArbitrationArchiveReference>,
)

data class ArbitrationArchiveReference(val runKey: String, val factsHash: String)

data class ArbitrationLastFileState(var fileDigest: String = "", var manifest: String = "")

/** 查询时必须同时匹配上传者关联。每次仅加载所选一局。 */
data class ArbitrationArchivePayload(
    var runKey: String = "",
    var recordId: Long? = null,
    var factsHash: String = "",
    var parserRevision: Int = 0,
    var detailRank: Int? = null,
    var hasConflict: Boolean = false,
    var structuredGzip: ByteArray = byteArrayOf(),
)