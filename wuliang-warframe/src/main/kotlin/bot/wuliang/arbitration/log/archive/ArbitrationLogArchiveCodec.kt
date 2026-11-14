package bot.wuliang.arbitration.log.archive

import bot.wuliang.arbitration.log.ArbitrationLogMath
import bot.wuliang.arbitration.log.ArbitrationLogParseResult
import bot.wuliang.arbitration.log.ArbitrationLogRun
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule
import org.springframework.stereotype.Component
import java.io.*
import java.security.DigestOutputStream
import java.security.MessageDigest
import java.util.*
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

@Component
class ArbitrationLogArchiveCodec(private val json: ObjectMapper) {
    private val decoder =
        json.copy().registerModule(KotlinModule.Builder().enable(KotlinFeature.StrictNullChecks).build())
            .enable(
                DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES,
                DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES,
                DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,
                DeserializationFeature.FAIL_ON_TRAILING_TOKENS
            )

    private data class Envelope(
        val format: Int, val detailsCollected: Boolean, val fileWarnings: List<String>,
        val inputIncomplete: Boolean, val run: ArbitrationLogRun,
    )

    fun encode(
        run: ArbitrationLogRun, fileWarnings: List<String>, fileDigest: String,
        inputIncomplete: Boolean
    ): ArbitrationArchivedRun {
        val flags = buildList {
            if (!run.completed || run.hostDurationSeconds == null) add("INCOMPLETE")
            if (run.startedAt == null) add("MISSING_CLOCK")
            if (run.sessionHash == null) add("MISSING_SESSION")
            if (run.gameBuild == null) add("UNKNOWN_BUILD")
            if (run.enemies == null || run.enemies <= 0) add("NO_ENEMY_COUNT")
            if (run.rounds == null || run.missionKind == "unknown") add("UNKNOWN_ROUNDS")
            if (run.warnings.isNotEmpty() || inputIncomplete) add("PARSE_WARNING")
        }
        val duration = run.hostDurationSeconds?.takeIf { run.completed && it > 0 }
        val fullVitus = run.rounds?.let {
            ArbitrationLogMath.roundsExpected(it) +
                    run.drones * ArbitrationLogMath.DROP_CHANCE * ArbitrationLogMath.FULL_MULTIPLIER
        }
        val bytes = ByteArrayOutputStream()
        GZIPOutputStream(LimitedOutputStream(bytes, MAX_PAYLOAD)).use {
            json.writeValue(
                LimitedOutputStream(it, MAX_JSON), mapOf(
                    "format" to 1, "detailsCollected" to true,
                    "fileWarnings" to fileWarnings, "inputIncomplete" to inputIncomplete, "run" to run
                )
            )
        }
        return ArbitrationArchivedRun(
            runKey(run, fileDigest),
            factsHash(run),
            run.nodeId,
            run.missionKind,
            run.gameBuild,
            run.clientType,
            run.startedAt,
            run.playedAt,
            duration,
            run.observedDurationSeconds,
            run.drones,
            run.enemies,
            run.waves,
            run.rounds,
            run.completed,
            flags.isEmpty(),
            json.writeValueAsString(flags),
            duration?.let { run.drones * 60 / it },
            duration?.let { time -> run.enemies?.times(60.0)?.div(time) },
            fullVitus,
            duration?.let { time -> fullVitus?.times(3600)?.div(time) },
            if (run.detailsTruncated) 1 else 2,
            bytes.toByteArray(),
        )
    }

    fun manifest(parsed: ArbitrationLogParseResult, records: List<ArbitrationArchivedRun>): String {
        val value = ArbitrationArchiveManifest(
            1, ARBITRATION_PARSER_REVISION, parsed.validTotal, parsed.excludedShort,
            parsed.excludedNonHost, parsed.warnings, parsed.inputIncomplete,
            records.map { ArbitrationArchiveReference(it.runKey, it.factsHash) })
        return json.writeValueAsString(value)
            .also { require(it.toByteArray(Charsets.UTF_8).size <= MAX_MANIFEST) { INVALID_ARCHIVE } }
    }

    fun readManifest(value: String): ArbitrationArchiveManifest {
        require(value.toByteArray(Charsets.UTF_8).size <= MAX_MANIFEST) { INVALID_ARCHIVE }
        val result = read { decoder.readValue(value, ArbitrationArchiveManifest::class.java) }
        require(
            result.format == 1 && result.parserRevision == ARBITRATION_PARSER_REVISION &&
                result.runs.size in 1..20 && result.validTotal >= result.runs.size &&
                result.excludedShort >= 0 && result.excludedNonHost >= 0 &&
                result.runs.all { HASH.matches(it.runKey) && HASH.matches(it.factsHash) } &&
                result.runs.map { it.runKey }.distinct().size == result.runs.size
        ) { INVALID_ARCHIVE }
        return result
    }

    fun decode(
        payload: ArbitrationArchivePayload,
        reference: ArbitrationArchiveReference,
        fileDigest: String
    ): ArbitrationLogRun {
        require(HASH.matches(fileDigest)) { INVALID_ARCHIVE }
        val run = decodeEnvelope(payload, reference).run
        require(runKey(run, fileDigest) == reference.runKey) { INVALID_ARCHIVE }
        return run
    }

    /** 按上传关联读取历史单局，不依赖发送者最近文件的摘要或局序。 */
    fun decodeRecord(payload: ArbitrationArchivePayload): ArbitrationLogParseResult {
        require(payload.recordId != null && payload.recordId!! > 0) { INVALID_ARCHIVE }
        val envelope = decodeEnvelope(payload, ArbitrationArchiveReference(payload.runKey, payload.factsHash))
        // 缺会话时run_key是数据库中的不透明标识，不能反推出原文件摘要。
        val sessionKey = sessionRunKey(envelope.run)
        require(sessionKey == null || sessionKey == payload.runKey) { INVALID_ARCHIVE }
        return ArbitrationLogParseResult(
            listOf(envelope.run), 1, 0, 0, envelope.fileWarnings, envelope.inputIncomplete
        )
    }

    private fun decodeEnvelope(
        payload: ArbitrationArchivePayload, reference: ArbitrationArchiveReference
    ): Envelope {
        val detailRank = payload.detailRank
        require(
            HASH.matches(reference.runKey) && HASH.matches(reference.factsHash) &&
                    payload.parserRevision == ARBITRATION_PARSER_REVISION &&
                    payload.factsHash == reference.factsHash && !payload.hasConflict && (detailRank == 1 || detailRank == 2) &&
                    payload.structuredGzip.size in 1..MAX_PAYLOAD
        ) { INVALID_ARCHIVE }
        val envelope = read {
            GZIPInputStream(ByteArrayInputStream(payload.structuredGzip)).use {
                decoder.readValue(LimitedInputStream(it, MAX_JSON), Envelope::class.java)
            }
        }
        require(envelope.format == 1 && envelope.detailsCollected) { INVALID_ARCHIVE }
        val run = envelope.run
        require(
            factsHash(run) == reference.factsHash &&
                    run.livingSamples.size <= 20000 && run.droneBursts.size <= 200000
        ) { INVALID_ARCHIVE }
        return envelope
    }

    private fun runKey(run: ArbitrationLogRun, fileDigest: String): String =
        sessionRunKey(run) ?: archiveHash("file|$fileDigest|${run.nodeId}|${run.startSeconds}")

    private fun sessionRunKey(run: ArbitrationLogRun): String? {
        require(run.sessionHash == null || HASH.matches(run.sessionHash)) { INVALID_ARCHIVE }
        return run.sessionHash?.let { archiveHash("session|$it|${run.nodeId}|${run.startSeconds}") }
    }

    private fun factsHash(run: ArbitrationLogRun): String {
        val digest = MessageDigest.getInstance("SHA-256")
        DigestOutputStream(OutputStream.nullOutputStream(), digest).use {
            json.writeValue(
                it, listOf(
                    run.nodeId, run.missionKind, run.drones, run.enemies,
                    run.hostDurationSeconds, run.waves, run.rounds, run.completed, run.phases
                )
            )
        }
        return HexFormat.of().formatHex(digest.digest())
    }

    private fun <T> read(action: () -> T): T = try {
        action()
    } catch (error: IOException) {
        throw IllegalArgumentException(INVALID_ARCHIVE, error)
    }

    /** 限制展开大小。 */
    private class LimitedInputStream(input: InputStream, private val maximum: Int) : FilterInputStream(input) {
        private var count = 0
        private fun account(size: Int) {
            if (size <= 0) return
            require(size <= maximum - count) { INVALID_ARCHIVE }
            count += size
        }

        override fun read(): Int = `in`.read().also { if (it >= 0) account(1) }
        override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
            `in`.read(buffer, offset, length).also(::account)
    }

    /** 限制压缩后单局大小。超限按归档失败处理，仍可生成本次图片。 */
    private class LimitedOutputStream(out: OutputStream, private val maximum: Int) : FilterOutputStream(out) {
        private var count = 0
        private fun account(size: Int) {
            require(size <= maximum - count) { "单局结构化记录超过归档大小上限。" }
            count += size
        }

        override fun write(value: Int) {
            account(1); out.write(value)
        }

        override fun write(buffer: ByteArray, offset: Int, length: Int) {
            account(length); out.write(buffer, offset, length)
        }
    }

    private companion object {
        const val MAX_PAYLOAD = 1024 * 1024
        const val MAX_JSON = 32 * 1024 * 1024
        const val MAX_MANIFEST = 32768
        const val INVALID_ARCHIVE = "这局的归档已变更、存在冲突或格式不可用，请重新发送 EE.log 进行本次分析。"
        val HASH = Regex("[0-9a-f]{64}")
    }
}