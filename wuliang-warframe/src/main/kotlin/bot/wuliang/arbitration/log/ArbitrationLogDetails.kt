package bot.wuliang.arbitration.log

import kotlin.math.ceil
import kotlin.math.floor

data class ArbitrationLogProficiency(
    val percent: Double?, val droneChancePercent: Double?, val expectedDrones: Double?,
    val kpmBaseline: Double?, val reason: String?,
)

data class ArbitrationLogTimelinePoint(
    val time: Double, val seconds: Double, val alivePeak: Int?, val aliveAverage: Double?,
    val dronesPerMinute: Double, val paused: Boolean, val gap: Boolean,
)

data class ArbitrationLogPhaseTime(
    val index: Int, val seconds: Double?, val completed: Boolean, val preparation: Boolean, val slow: Boolean,
)

data class ArbitrationLogAliveShare(val alive: Int, val percent: Double)
data class ArbitrationLogSaturation(
    val maximumAlive: Int, val medianAlive: Int, val p90Alive: Int,
    val highPercent: Double, val activeSeconds: Double, val distribution: List<ArbitrationLogAliveShare>,
)

data class ArbitrationLogInterval(val start: Double, val end: Double)
data class ArbitrationLogGapBucket(val lower: Double, val upper: Double?, val percent: Double)
data class ArbitrationLogGaps(
    val count: Int, val totalSeconds: Double, val longestSeconds: Double, val perHour: Double,
    val pauseSeconds: Double, val pausePercent: Double,
    val over2Percent: Double?, val atLeast10Percent: Double?, val atLeast30Percent: Double?,
    val distribution: List<ArbitrationLogGapBucket>, val longest: List<ArbitrationLogInterval>,
)

data class ArbitrationLogDetailReport(
    val proficiency: ArbitrationLogProficiency,
    val durationSeconds: Double, val binSeconds: Int, val dpmWindowSeconds: Int,
    val timeline: List<ArbitrationLogTimelinePoint>, val phaseBoundaries: List<Double>,
    val phaseTimes: List<ArbitrationLogPhaseTime>, val phaseMedianSeconds: Double?, val phaseBestSeconds: Double?,
    val phaseSlowThresholdSeconds: Double?,
    val saturation: ArbitrationLogSaturation?, val gaps: ArbitrationLogGaps?, val warnings: List<String>,
)

/** 从日志采样计算详情。停刷用存活数和采样间隔识别 */
object ArbitrationLogDetails {
    private val gapEdges =
        doubleArrayOf(0.0, 0.5, 1.0, 1.5, 2.0, 3.0, 4.0, 6.0, 8.0, 10.0, 15.0, 20.0, 30.0, 40.0, 50.0, 60.0)

    fun proficiency(drones: Int, enemyCount: Int?, durationSeconds: Double?, kpmBaseline: Double?): ArbitrationLogProficiency {
        val enemies = enemyCount?.takeIf { it > 0 }
        val density = enemies?.let { drones * 100.0 / it }
        val knownDuration = durationSeconds?.takeIf { it.isFinite() && it > 0 }
        val kpm =
            if (enemies != null && knownDuration != null) enemies * 60.0 / knownDuration else null
        val baseline = kpmBaseline?.takeIf { it.isFinite() && it > 0 }
        return ArbitrationLogProficiency(
            if (kpm != null && baseline != null && density != null) kpm / baseline * density / 20 * 100 else null,
            density, enemies?.times(0.2), baseline,
            when {
                baseline == null -> "缺少该节点的 KPM 基准"; kpm == null -> "缺少完整计时或敌人生成数"; else -> null
            },
        )
    }

    fun analyze(run: ArbitrationLogRun, kpmBaseline: Double?): ArbitrationLogDetailReport {
        val knownDuration = run.hostDurationSeconds
        val proficiency = proficiency(run.drones, run.enemies, knownDuration, kpmBaseline)
        val duration = knownDuration ?: maxOf(
            run.livingSamples.lastOrNull()?.time ?: 0.0,
            run.droneBursts.lastOrNull()?.time ?: 0.0
        )
        val visiblePhases = run.phases.filter { it.kind == "wave" || it.drones > 0 || it.index <= (run.rounds ?: 0) }
        val phaseSeconds = visiblePhases.filter { it.index > 1 && it.completed }
            .mapNotNull { it.durationSeconds?.takeIf { n -> n.isFinite() && n >= 0 } }.sorted()
        val median = if (phaseSeconds.isEmpty()) null else if (phaseSeconds.size % 2 == 0)
            (phaseSeconds[phaseSeconds.size / 2 - 1] + phaseSeconds[phaseSeconds.size / 2]) / 2 else phaseSeconds[phaseSeconds.size / 2]
        val slowThreshold = if (phaseSeconds.isEmpty()) null else {
            val position = (phaseSeconds.size - 1) * 0.9
            val lower = floor(position).toInt()
            phaseSeconds[lower] + (phaseSeconds[minOf(
                lower + 1,
                phaseSeconds.lastIndex
            )] - phaseSeconds[lower]) * (position - lower)
        }
        val phases = visiblePhases.map {
            val seconds = it.durationSeconds?.takeIf { n -> n.isFinite() && n >= 0 }
            ArbitrationLogPhaseTime(
                it.index, seconds, it.completed, it.index == 1,
                it.index > 1 && it.completed && seconds != null && slowThreshold != null && seconds + 1e-9 >= slowThreshold
            )
        }
        val warnings = mutableListOf<String>()
        fun unavailable(message: String) = ArbitrationLogDetailReport(
            proficiency,
            duration,
            5,
            65,
            emptyList(),
            emptyList(),
            phases,
            median,
            phaseSeconds.firstOrNull(),
            slowThreshold,
            null,
            null,
            listOf(message),
        )
        if (run.detailsTruncated) return unavailable("详情采样超过处理上限，时间轴、饱和度和断流不计算。")
        if (!duration.isFinite() || duration <= 0 || duration > 7 * 24 * 3600) return unavailable("缺少有效时间范围，无法生成战斗详情。")
        val samples = run.livingSamples.filter { it.time in 0.0..duration }
        val bursts = run.droneBursts.filter { it.time in 0.0..duration }
        val boundaries = run.phaseBoundaries.filter { it in 0.0..duration }.sorted()
        val pauses = pauses(samples, boundaries, duration)
        val binSeconds = maxOf(5, ceil(duration / 600).toInt())
        val count = ceil(duration / binSeconds).toInt().coerceAtLeast(1)
        val aliveWeight = DoubleArray(count)
        val observedSeconds = DoubleArray(count)
        val alivePeak = IntArray(count) { -1 }
        val droneCounts = IntArray(count)
        val pauseFlags = BooleanArray(count)
        val gapFlags = BooleanArray(count)
        val aliveSeconds = DoubleArray(1000)

        fun mark(flags: BooleanArray, start: Double, end: Double) {
            val from = floor(start.coerceAtLeast(0.0) / binSeconds).toInt().coerceIn(0, count - 1)
            val to = (ceil(end.coerceAtMost(duration) / binSeconds).toInt() - 1).coerceIn(0, count - 1)
            if (end > start) for (index in from..to) flags[index] = true
        }
        for (pause in pauses) mark(pauseFlags, pause.start, pause.end)
        for (index in 0 until samples.lastIndex) {
            val a = samples[index]
            val b = samples[index + 1]
            val span = b.time - a.time
            if (span <= 0 || span > 10) continue
            val alive = a.alive.coerceIn(0, 999)
            if (!inside(pauses, a.time)) aliveSeconds[alive] += span
            val first = floor(a.time / binSeconds).toInt().coerceIn(0, count - 1)
            val last = (ceil(b.time / binSeconds).toInt() - 1).coerceIn(0, count - 1)
            for (bucket in first..last) {
                val seconds = minOf(b.time, (bucket + 1.0) * binSeconds) - maxOf(a.time, bucket.toDouble() * binSeconds)
                if (seconds <= 0) continue
                aliveWeight[bucket] += alive * seconds
                observedSeconds[bucket] += seconds
                alivePeak[bucket] = maxOf(alivePeak[bucket], alive)
            }
        }
        for (burst in bursts) droneCounts[floor(burst.time / binSeconds).toInt().coerceIn(0, count - 1)] += burst.count
        val prefix = LongArray(count + 1)
        for (index in droneCounts.indices) prefix[index + 1] = prefix[index] + droneCounts[index]
        val radius = floor((maxOf(60.0, 3.0 * binSeconds) / binSeconds - 1) / 2 + 0.5).toInt().coerceAtLeast(0)
        val window = (2 * radius + 1) * binSeconds

        val activeSeconds = aliveSeconds.sum()
        fun quantile(p: Double): Int {
            var weight = 0.0
            for (index in aliveSeconds.indices) {
                weight += aliveSeconds[index]
                if (weight >= activeSeconds * p - 1e-9) return index
            }
            return aliveSeconds.lastIndex
        }

        val saturation = if (activeSeconds > 0) ArbitrationLogSaturation(
            samples.maxOf { it.alive }, quantile(0.5), quantile(0.9), aliveSeconds.drop(15).sum() * 100 / activeSeconds,
            activeSeconds,
            aliveSeconds.indices.mapNotNull { alive ->
                val seconds = aliveSeconds[alive]
                if (seconds > 0) ArbitrationLogAliveShare(alive, seconds * 100 / activeSeconds) else null
            },
        ) else null
        if (samples.size < 2) warnings.add("缺少存活敌人记录，无法计算饱和度或区分停刷与战斗断流。")

        var gapCount = 0
        var gapTotal = 0.0
        var gapLongest = 0.0
        var gapActiveSeconds = 0.0
        var over2Seconds = 0.0
        var over10Seconds = 0.0
        var over30Seconds = 0.0
        val gapWeights = DoubleArray(gapEdges.size - 1)
        val longest = mutableListOf<ArbitrationLogInterval>()
        if (samples.size >= 2) for (index in 0 until bursts.lastIndex) {
            val start = bursts[index].time
            val end = bursts[index + 1].time
            val span = end - start
            if (span <= 0 || inside(pauses, start + span / 2)) continue
            gapActiveSeconds += span
            if (span > 2) over2Seconds += span
            if (span >= 10) over10Seconds += span
            if (span >= 30) over30Seconds += span
            val bucket = (0 until gapEdges.lastIndex).firstOrNull { span < gapEdges[it + 1] } ?: gapWeights.lastIndex
            gapWeights[bucket] += span
            if (span < 10) continue
            // 时间轴和长断流摘要按0.1秒输出，与参考页的显示精度一致。
            val shownStart = tenths(start)
            val shownSpan = tenths(span)
            gapCount++
            gapTotal += shownSpan
            gapLongest = maxOf(gapLongest, shownSpan)
            mark(gapFlags, shownStart, shownStart + shownSpan)
            longest.add(ArbitrationLogInterval(shownStart, shownStart + shownSpan))
            longest.sortByDescending { it.end - it.start }
            if (longest.size > 4) longest.removeAt(longest.lastIndex)
        }
        val pauseSeconds = pauses.sumOf { (tenths(it.end) - tenths(it.start)).coerceAtLeast(0.0) }
        val gaps = if (samples.size >= 2 && bursts.size >= 2) ArbitrationLogGaps(
            gapCount, gapTotal, gapLongest, gapCount * 3600 / duration, pauseSeconds, pauseSeconds * 100 / duration,
            if (gapActiveSeconds > 0) over2Seconds * 100 / gapActiveSeconds else null,
            if (gapActiveSeconds > 0) over10Seconds * 100 / gapActiveSeconds else null,
            if (gapActiveSeconds > 0) over30Seconds * 100 / gapActiveSeconds else null,
            gapWeights.mapIndexed { index, seconds ->
                ArbitrationLogGapBucket(
                    gapEdges[index],
                    gapEdges[index + 1].takeIf { index < gapWeights.lastIndex },
                    if (gapActiveSeconds > 0) seconds * 100 / gapActiveSeconds else 0.0
                )
            },
            longest,
        ) else null
        val timeline = (0 until count).map { index ->
            val low = maxOf(0, index - radius)
            val high = minOf(count - 1, index + radius)
            val seconds = minOf(duration, (high + 1.0) * binSeconds) - low.toDouble() * binSeconds
            ArbitrationLogTimelinePoint(
                index.toDouble() * binSeconds,
                minOf(binSeconds.toDouble(), duration - index.toDouble() * binSeconds),
                alivePeak[index].takeIf { it >= 0 },
                if (observedSeconds[index] > 0) aliveWeight[index] / observedSeconds[index] else null,
                if (seconds > 0) (prefix[high + 1] - prefix[low]) * 60.0 / seconds else 0.0,
                pauseFlags[index],
                gapFlags[index]
            )
        }
        return ArbitrationLogDetailReport(
            proficiency, duration, binSeconds, window, timeline, boundaries, phases, median,
            phaseSeconds.firstOrNull(), slowThreshold, saturation, gaps, warnings
        )
    }

    private fun tenths(value: Double) = floor(value * 10 + 0.5) / 10

    private fun inside(intervals: List<ArbitrationLogInterval>, time: Double): Boolean {
        var low = 0
        var high = intervals.lastIndex
        var match = -1
        while (low <= high) {
            val middle = (low + high) ushr 1
            if (intervals[middle].start <= time) {
                match = middle; low = middle + 1
            } else high = middle - 1
        }
        return match >= 0 && time < intervals[match].end
    }

    private fun pauses(
        samples: List<ArbitrationLogLivingSample>,
        boundaries: List<Double>,
        duration: Double
    ): List<ArbitrationLogInterval> {
        val ranges = mutableListOf<ArbitrationLogInterval>()
        var zeroStart: Int? = null
        fun add(start: Double, end: Double) {
            val a = start.coerceAtLeast(0.0)
            val b = end.coerceAtMost(duration)
            if (b > a) ranges.add(ArbitrationLogInterval(a, b))
        }
        for (index in samples.indices) {
            if (samples[index].alive == 0) {
                if (zeroStart == null) zeroStart = index
            } else if (zeroStart != null) {
                val start = samples[zeroStart].time
                if (zeroStart == 0 || samples[index].time - start >= 3) add(start, samples[index].time)
                zeroStart = null
            }
        }
        zeroStart?.let {
            if (it == 0 || samples.last().time - samples[it].time >= 3) add(
                samples[it].time,
                samples.last().time
            )
        }
        for (index in 0 until samples.lastIndex) {
            val a = samples[index]
            val b = samples[index + 1]
            val span = b.time - a.time
            if (span >= 3 && (index == 0 || a.alive == 0 || b.alive == 0 || span >= 20)) add(a.time, b.time)
        }
        var position = 0
        for (boundary in boundaries) {
            while (position < samples.lastIndex && samples[position + 1].time <= boundary) position++
            if (position < samples.lastIndex && samples[position].time <= boundary && samples[position + 1].time - samples[position].time >= 3)
                add(samples[position].time, samples[position + 1].time)
        }
        val merged = mutableListOf<ArbitrationLogInterval>()
        for (range in ranges.sortedBy { it.start }) {
            val previous = merged.lastOrNull()
            if (previous != null && range.start <= previous.end) merged[merged.lastIndex] =
                previous.copy(end = maxOf(previous.end, range.end))
            else merged.add(range)
        }
        return merged
    }
}