package bot.wuliang.arbitration.log

import java.io.Reader
import java.security.MessageDigest
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.HexFormat
import java.util.Locale
import kotlin.collections.ArrayDeque
import kotlin.collections.isNotEmpty
import kotlin.collections.last
import kotlin.collections.lastIndex
import kotlin.collections.lastOrNull
import kotlin.collections.linkedSetOf
import kotlin.collections.map
import kotlin.collections.mutableListOf
import kotlin.collections.reversed
import kotlin.collections.setOf
import kotlin.collections.toList

/** 单遍读取。只保存有限的统计，不保留原日志、队友名称、账号和网络地址。 */
class ArbitrationLogParser {
    private data class Phase(
        val index: Int,
        val kind: String,
        val start: Double,
        val enemiesBefore: Int?,
        var drones: Int = 0,
        var end: Double? = null,
        var enemiesAfter: Int? = null,
        var completed: Boolean = false,
    )

    private class Run(
        val nodeId: String, val start: Double, val startedAt: Long?,
        val gameBuild: String?, val clientType: String?, val sessionHash: String?,
    ) {
        var lastEvent = start
        var eom: Double? = null
        var enemies: Int? = null
        var hostEvents = 0
        var drones = 0
        var waves = 0
        var rounds = 0
        var kind = "unknown"
        var migration = false
        var current: Phase? = null
        val phases = mutableListOf<Phase>()
        val warnings = linkedSetOf<String>()
        val livingSamples = mutableListOf<ArbitrationLogLivingSample>()
        val droneBursts = mutableListOf<ArbitrationLogDroneBurst>()
        val phaseBoundaries = mutableListOf<Double>()
        var lastAgentWasDrone = false
        var detailsTruncated = false

        fun markBoundary(time: Double) {
            phaseBoundaries.add(time - start)
        }

        fun recordAgent(time: Double, body: String, drone: Boolean) {
            val relative = time - start
            if (relative < 0) return
            ALIVE.find(body)?.groupValues?.get(1)?.toIntOrNull()?.let { value ->
                if (value > 999) {
                    detailsTruncated = true; return@let
                }
                val previous = livingSamples.lastOrNull()
                if (previous != null && relative.toInt() == previous.time.toInt()) {
                    if (value > previous.alive) livingSamples[livingSamples.lastIndex] = previous.copy(alive = value)
                } else if (previous == null || relative > previous.time) {
                    if (livingSamples.size < MAX_SAMPLES) livingSamples.add(ArbitrationLogLivingSample(relative, value))
                    else detailsTruncated = true
                }
            }
            if (drone) {
                if (lastAgentWasDrone && droneBursts.isNotEmpty()) {
                    val previous = droneBursts.last()
                    droneBursts[droneBursts.lastIndex] = previous.copy(count = previous.count + 1)
                } else if (droneBursts.size < MAX_BURSTS) droneBursts.add(ArbitrationLogDroneBurst(relative, 1))
                else detailsTruncated = true
            }
            lastAgentWasDrone = drone
        }

        fun boundary(index: Int, kind: String, time: Double) {
            if (index <= 0) return
            if (current?.index == index && current?.kind == kind) return
            current?.let {
                it.end = time
                it.enemiesAfter = enemies
                it.completed = true
            }
            val phase = Phase(index, kind, if (phases.isEmpty()) start else time, if (phases.isEmpty()) 0 else enemies)
            phases.add(phase)
            current = phase
        }
    }

    fun parse(reader: Reader, details: Boolean): ArbitrationLogParseResult {
        val runs = ArrayDeque<ArbitrationLogRun>()
        val warnings = linkedSetOf<String>()
        var candidate: String? = null
        var candidateTime = 0.0
        var current: Run? = null
        var utcAnchor: Long? = null
        var anchorSeconds = 0.0
        var gameBuild: String? = null
        var clientType: String? = null
        var hostSessionHash: String? = null
        var valid = 0
        var short = 0
        var nonHost = 0
        var recognized = false
        var inputIncomplete = false

        fun finish(ended: Boolean) {
            val run = current ?: return
            current = null
            if (run.hostEvents == 0 || (run.drones == 0 && (run.enemies ?: 0) == 0)) {
                nonHost++
                return
            }
            val end = run.eom?.takeIf { ended && it > run.start && !run.migration }
            val duration = end?.minus(run.start)
            if ((duration ?: (run.lastEvent - run.start)) < 60) {
                short++
                return
            }
            if (end == null) run.warnings.add(if (run.migration) "发生主机迁移，无法还原完整计时。" else "结算或开局记录不完整，效率不计算。")
            run.current?.let {
                it.end = end
                it.enemiesAfter = run.enemies
                if (it.kind != "wave") it.completed = end != null
            }
            val phases = run.phases.map { phase ->
                val enemies = if (phase.enemiesBefore != null && phase.enemiesAfter != null)
                    (phase.enemiesAfter!! - phase.enemiesBefore).coerceAtLeast(0) else null
                ArbitrationLogPhase(
                    phase.index, phase.kind, phase.drones, enemies,
                    phase.end?.minus(phase.start)?.takeIf { it >= 0 }, phase.completed, phase.start - run.start
                )
            }
            val rounds = when (run.kind) {
                "defense", "mirrorDefense", "interception", "survival", "disruption" -> run.rounds
                else -> null
            }
            if (rounds == null) run.warnings.add("未识别到该任务的轮次记录，只展示无人机掉落期望。")
            valid++
            runs.addLast(
                ArbitrationLogRun(
                    run.nodeId,
                    run.kind,
                    run.drones,
                    run.enemies,
                    duration,
                    run.waves.takeIf { it > 0 },
                    rounds,
                    phases,
                    end != null,
                    utcAnchor?.let { it + (((end ?: run.lastEvent) - anchorSeconds) * 1000).toLong() },
                    run.warnings.toList(),
                    run.livingSamples,
                    run.droneBursts,
                    run.phaseBoundaries,
                    run.detailsTruncated,
                    run.startedAt,
                    run.start,
                    (run.lastEvent - run.start).coerceAtLeast(0.0),
                    run.gameBuild,
                    run.clientType,
                    run.sessionHash,
                )
            )
            if (runs.size > MAX_RUNS) runs.removeFirst()
        }

        fun feed(raw: String) {
            val line = raw.removePrefix("\uFEFF")
            val stamp = TIMESTAMP.find(line)
            if (stamp == null) {
                // 引擎有时单独输出结束状态。时间仍取带时间戳的结算事件，不能取后续迁移时间。
                if (line == ENDING_STATE) finish(true)
                return
            }
            val time = stamp.groupValues[1].toDoubleOrNull()?.takeIf { it.isFinite() } ?: return
            val body = line.substring(stamp.range.last + 1)
            // 只采用成功创建的主机会话。新建或成功删除时清空，避免借用上一支队伍的会话。
            if (body.startsWith("Net [Info]:")) {
                if (body == "Net [Info]: MatchingService::HostSession" || SESSION_DELETED.containsMatchIn(body)) {
                    hostSessionHash = null
                }
                HOST_SESSION.find(body)?.groupValues?.get(1)?.let { session ->
                    hostSessionHash = HexFormat.of().formatHex(
                        MessageDigest.getInstance("SHA-256")
                            .digest(session.lowercase(Locale.ROOT).toByteArray(Charsets.UTF_8))
                    )
                }
            }
            if (body.startsWith("Sys [Diag]:")) {
                BUILD.find(body)?.groupValues?.get(1)?.let { gameBuild = it }
                CLIENT.find(body)?.groupValues?.get(1)?.let { clientType = it.lowercase(Locale.ROOT) }
            }
            if (body.startsWith("Sys [Diag]: Current time:")) {
                recognized = true
                UTC.find(body)?.groupValues?.get(1)?.let { text ->
                    utcAnchor = runCatching {
                        LocalDateTime.parse(text.trim().replace(Regex(" +"), " "), UTC_FORMAT)
                            .toInstant(ZoneOffset.UTC).toEpochMilli()
                    }.getOrNull()
                    anchorSeconds = time
                }
            }
            // Background.lua 的 EliteAlertMission 是当前排期，不能据此认定玩家进入了仲裁。
            if (body.startsWith("Script [Info]: ThemedSquadOverlay.lua: Host loading ")) {
                val node = ELITE.find(body)?.groupValues?.get(1)
                if (current != null) finish(false)
                candidate = node
                candidateTime = time
            } else if (body.startsWith("Net [Info]: Set squad mission:") ||
                body.startsWith("Script [Info]: MapRedux.lua: Confirm sector ") ||
                body.startsWith("Script [Info]: ThemedSquadOverlay.lua: LoadLevelMsg received.")
            ) {
                ELITE.find(body)?.groupValues?.get(1)?.let {
                    candidate = it
                    candidateTime = time
                }
            }
            if (body == "Net [Info]: GameRulesImpl - changing state from SS_WAITING_FOR_PLAYERS to SS_STARTED") {
                recognized = true
                if (current != null) finish(false)
                if (candidate != null && time - candidateTime in 0.0..180.0) {
                    current = Run(
                        candidate!!,
                        time,
                        utcAnchor?.let { it + ((time - anchorSeconds) * 1000).toLong() },
                        gameBuild,
                        clientType,
                        hostSessionHash
                    )
                    candidate = null
                }
            }
            val run = current ?: return
            if (body.startsWith("Game [Info]: FinalizeHostMigration, have data for ")) {
                run.migration = true
            }
            if (body == "Net [Info]: $ENDING_STATE") {
                finish(true)
                return
            }
            if (body.startsWith("Script [Info]: EndOfMatch.lua: Initialize") ||
                body.startsWith("Script [Info]: ExtractionTimer.lua: EOM: All players extracting")
            ) {
                run.eom = time
                run.lastEvent = time
            }
            if (body.startsWith("Script [Info]: WaveDefend.lua: Defense wave:")) {
                val wave = body.substringAfterLast(':').trim().toIntOrNull() ?: return
                if (wave > run.waves) {
                    run.kind = "defense"
                    run.waves = wave
                    if (details) run.markBoundary(time)
                    run.boundary(wave, "wave", time)
                    run.lastEvent = time
                }
            }
            if (body.startsWith("Script [Info]: LoopDefend.lua: Loop Defense wave:")) {
                val wave = body.substringAfterLast(':').trim().toIntOrNull() ?: return
                if (wave > run.waves) {
                    run.kind = "mirrorDefense"
                    run.waves = wave
                    if (details) run.markBoundary(time)
                    run.boundary(wave, "wave", time)
                    run.lastEvent = time
                }
            }
            if (body.startsWith("Script [Info]: WaveDefend.lua: _SleepBetweenWaves(")) {
                run.current?.takeIf { it.kind == "wave" }?.completed = true
            }
            if (body == "Script [Info]: HudRedux.lua: Queuing new transmission: InterNewRoundLotusTransmission") {
                run.kind = "interception"
                if (run.current == null) run.boundary(1, "round", run.start)
                run.rounds++
                if (details) run.markBoundary(time)
                run.boundary(run.rounds + 1, "round", time)
                run.lastEvent = time
            }
            if (body == "Script [Info]: DefenseReward.lua: DefenseReward::TransitionOut" &&
                run.kind in setOf("defense", "mirrorDefense")
            ) {
                run.rounds++
                run.current?.takeIf { it.kind == "wave" }?.completed = true
            }
            if (body.startsWith("Script [Info]: SurvivalMission.lua: Survival: Gave reward tier ")) {
                val round = SURVIVAL.find(body)?.groupValues?.get(1)?.toIntOrNull() ?: return
                if (run.current == null) run.boundary(1, "round", run.start)
                run.kind = "survival"
                run.rounds = round
                if (details) run.markBoundary(time)
                run.boundary(round + 1, "round", time)
                run.lastEvent = time
            }
            if (body.startsWith("Script [Info]: SentientArtifactMission.lua: Disruption: State change: ")) {
                run.kind = "disruption"
                if (body.endsWith("ARTIFACT_ROUND")) run.boundary(run.rounds + 1, "round", time)
                if (body.endsWith("ARTIFACT_ROUND_DONE")) {
                    run.rounds++
                    run.current?.let { it.end = time; it.enemiesAfter = run.enemies; it.completed = true }
                    run.current = null
                }
                run.lastEvent = time
            }
            if (body.startsWith("AI [Info]: OnAgentCreated /Npc/")) {
                recognized = true
                run.hostEvents++
                run.lastEvent = time
                // Spawned 是游戏的敌人累计计数。不能把所有 AI（含宠物、友军）累加。
                SPAWNED.find(body)?.groupValues?.get(1)?.toIntOrNull()?.let { count ->
                    if (run.enemies != null && count < run.enemies!!) run.migration = true
                    run.enemies = maxOf(run.enemies ?: 0, count)
                }
                val drone = DRONE.containsMatchIn(body)
                if (details) run.recordAgent(time, body, drone)
                if (drone) {
                    run.drones++
                    if (run.current == null && run.kind !in setOf("defense", "mirrorDefense", "disruption")) {
                        run.boundary(run.rounds + 1, "round", time)
                    }
                    run.current?.let { it.drones++ }
                }
            }
        }

        // 超长行直接略过，避免大文件或单行内容使内存随输入无限增长。
        val buffer = CharArray(16 * 1024)
        val line = StringBuilder(512)
        var oversized = false
        var chars = 0L
        while (true) {
            val size = reader.read(buffer)
            if (size < 0) break
            chars += size
            require(chars <= ArbitrationLogLimits.MAX_CHARACTERS) { "EE.log 超过${ArbitrationLogLimits.SIZE_LABEL}处理上限。" }
            for (index in 0 until size) {
                val char = buffer[index]
                if (char == '\n') {
                    if (!oversized) feed(line.toString().trimEnd('\r'))
                    else {
                        warnings.add("日志含超长行，已跳过；对应信息可能缺失。")
                        inputIncomplete = true
                    }
                    line.setLength(0)
                    oversized = false
                } else if (!oversized) {
                    if (line.length < MAX_LINE) line.append(char)
                    else {
                        oversized = true; line.setLength(0)
                    }
                }
            }
        }
        if (line.isNotEmpty() && !oversized) feed(line.toString().trimEnd('\r'))
        if (oversized) {
            warnings.add("日志含超长行，已跳过；对应信息可能缺失。")
            inputIncomplete = true
        }
        finish(false)
        require(recognized) { "没有识别到有效的 Warframe EE.log 内容，请发送原始日志文件。" }
        if (valid > MAX_RUNS) warnings.add("仅保留最近${MAX_RUNS}局可分析记录。")
        return ArbitrationLogParseResult(runs.reversed(), valid, short, nonHost, warnings.toList(), inputIncomplete)
    }

    private companion object {
        const val MAX_RUNS = 20
        const val MAX_SAMPLES = 20_000
        const val MAX_BURSTS = 200_000
        const val MAX_LINE = 16 * 1024
        const val ENDING_STATE = "GameRulesImpl - changing state from SS_STARTED to SS_ENDING"
        val TIMESTAMP = Regex("^!?(\\d+(?:\\.\\d+)?)\\s+")
        val ELITE = Regex("([A-Za-z][A-Za-z0-9]*)_EliteAlert\\b")
        val SPAWNED = Regex("\\bSpawned (\\d+)\\b")
        val DRONE = Regex("^AI \\[Info]: OnAgentCreated /Npc/CorpusEliteShieldDroneAgent\\d*\\b")
        val ALIVE = Regex("\\bMonitoredTicking (\\d+)\\b")
        val SURVIVAL = Regex("Gave reward tier (\\d+) at ")
        val UTC = Regex("\\[UTC: (.+?)]")
        val BUILD = Regex("^Sys \\[Diag]: Build Label: (\\d{4}\\.\\d{2}\\.\\d{2}\\.\\d{2}\\.\\d{2})\\b")
        val CLIENT = Regex("\\bclienttype:(Steam|WeGame|Epic|Standalone)\\b", RegexOption.IGNORE_CASE)
        val HOST_SESSION = Regex("^Net \\[Info]: HostSessionDone \\(1, sessionId=([0-9a-fA-F]{24})[,)]")
        val SESSION_DELETED = Regex("^Net \\[Info]: DeleteSessionCallback\\(1[,)]")
        val UTC_FORMAT = DateTimeFormatter.ofPattern("EEE MMM d HH:mm:ss yyyy", Locale.US)
    }
}