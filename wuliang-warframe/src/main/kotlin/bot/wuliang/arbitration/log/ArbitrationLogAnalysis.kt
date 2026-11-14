package bot.wuliang.arbitration.log

/** 由主机生成记录计算期望收益和可选详情。 */
object ArbitrationLogAnalysis {
    fun report(
        parsed: ArbitrationLogParseResult,
        options: ArbitrationLogOptions,
        node: String,
        missionType: String,
        faction: String,
        kpmBaseline: Double? = null,
    ): ArbitrationLogReport {
        require(parsed.runs.isNotEmpty()) {
            "没有找到可分析的主机仲裁记录。请确认文件来自主机，且包含至少1分钟的仲裁对局。"
        }
        require(options.run in 1..parsed.runs.size) { "这份日志可选择最近${parsed.runs.size}局。第1局是最近一局。" }
        val run = parsed.runs[options.run - 1]
        val duration = run.hostDurationSeconds.takeIf { run.completed }
        val multiplier = options.multiplier
        val dronesExpected = run.drones * ArbitrationLogMath.DROP_CHANCE * multiplier
        val roundsExpected = run.rounds?.let(ArbitrationLogMath::roundsExpected)
        val expected = roundsExpected?.plus(dronesExpected)
        fun hourly(amount: Double?): Double? = if (amount != null && duration != null && duration > 0)
            amount * 3600 / duration else null

        val fullExpected =
            roundsExpected?.plus(run.drones * ArbitrationLogMath.DROP_CHANCE * ArbitrationLogMath.FULL_MULTIPLIER)
        val normalized = hourly(fullExpected)
        val warnings = (parsed.warnings + run.warnings).toMutableList()
        if (parsed.excludedShort > 0) warnings.add("已排除${parsed.excludedShort}局不足1分钟的对局。")
        if (parsed.excludedNonHost > 0) warnings.add("已排除${parsed.excludedNonHost}局缺少主机生成记录的对局。")
        val phases = run.phases.filter { it.kind == "wave" || it.drones > 0 || it.index <= (run.rounds ?: 0) }
        val waveDetail = options.details && run.missionKind in setOf("defense", "mirrorDefense")
        val groups = if (!waveDetail && run.missionKind in setOf("defense", "mirrorDefense")) {
            val size = if (run.missionKind == "defense") 3 else 2
            phases.filter { it.kind == "wave" }.groupBy { (it.index - 1) / size }.values.toList()
        } else phases.map(::listOf)
        var cumulative = 0
        val allRows = groups.map { group ->
            val first = group.first()
            val last = group.last()
            val round = when (run.missionKind) {
                "defense" -> (first.index - 1) / 3 + 1
                "mirrorDefense" -> (first.index - 1) / 2 + 1
                else -> first.index
            }
            val roundCompleted = run.rounds != null && round <= run.rounds && group.all { it.completed }
            val completed = if (waveDetail) group.all { it.completed } else roundCompleted
            val rewardBoundary = !waveDetail || last.index % (if (run.missionKind == "defense") 3 else 2) == 0
            val reward =
                if (roundCompleted && rewardBoundary) ArbitrationLogMath.roundsExpected(round) - ArbitrationLogMath.roundsExpected(
                    round - 1
                ) else 0.0
            val drones = group.sumOf { it.drones }
            cumulative += drones
            ArbitrationLogRow(
                if (waveDetail) "第${first.index}波" else if (first.kind == "wave") "${first.index}–${last.index}波" else "第${first.index}轮",
                drones, cumulative,
                if (group.all { it.enemies != null }) group.sumOf { it.enemies!! } else null,
                if (group.all { it.durationSeconds != null }) group.sumOf { it.durationSeconds!! } else null,
                drones * ArbitrationLogMath.DROP_CHANCE * multiplier + reward, completed,
            )
        }
        if (allRows.sumOf { it.drones } != run.drones) warnings.add("部分生成记录无法分配到轮次，总数仍保留。")
        return ArbitrationLogReport(
            run.nodeId, node, missionType, faction, run.completed, run.playedAt,
            options.run, parsed.validTotal, run.drones, run.enemies, run.waves, run.rounds,
            duration,
            if (duration != null && duration > 0) run.drones * 60 / duration else null,
            if (duration != null && duration > 0) run.enemies?.times(60.0)?.div(duration) else null,
            multiplier,
            buildList {
                if (options.dropChance) add("掉落几率 ×2")
                if (options.resource) add("资源数量 ×2")
                if (options.blessing) add("掉率祝福 ×1.25")
                if (options.retriever) add("富足巡回者 ×1.18")
            },
            dronesExpected, roundsExpected, expected, hourly(expected), normalized,
            normalized?.let(ArbitrationLogMath::grade),
            allRows, if (waveDetail) "wave" else "round",
            if (options.details) ArbitrationLogDetails.analyze(run, kpmBaseline) else null,
            warnings.distinct(),
        )
    }
}