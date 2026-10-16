package bot.wuliang.worldstate.presentation

import bot.wuliang.moudles.*
import bot.wuliang.translation.PublicExportService
import bot.wuliang.translation.WorldStateCatalog
import bot.wuliang.utils.TimeUtils.formatDuration
import bot.wuliang.worldstate.parser.*
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant

/** 将可空的 Unix 毫秒时间戳转换为 [Instant]；接收值为空时返回 null。 */
internal fun Long?.asInstant(): Instant? = this?.let(Instant::ofEpochMilli)

/** 根据 [expiry] 和 [now] 生成剩余时间文本，两者单位均为 Unix 毫秒；结束时间为空时返回 null。 */
internal fun remainingTime(expiry: Long?, now: Long): String? = expiry?.let {
    formatDuration(Duration.ofMillis(it - now))
}

/** 将共用玩法数据映射为原有页面响应，字段名和日期类型保持兼容。 */
@Component
class MissionResponseMapper(
    private val translations: PublicExportService,
    private val catalog: WorldStateCatalog,
) {
    /** 翻译裂缝的节点、任务、阵营与等级，按 [now] 计算倒计时并保留原有页面字段。 */
    fun fissure(value: FissureState, now: Long): Fissure {
        val node = translations.node(value.node)
        val tier = catalog.tier(value.tier)
        return Fissure(
            id = value.id,
            activation = value.activation.asInstant(),
            expiry = value.expiry.asInstant(),
            eta = remainingTime(value.expiry, now)?.replace("\\s+".toRegex(), ""),
            node = node.name,
            missionType = value.missionType?.let(translations::name) ?: node.type ?: translations.name("MT_DEFAULT"),
            faction = node.faction,
            modifier = tier.value,
            modifierValue = tier.num,
            hard = value.hard,
            storm = value.storm,
        )
    }

    /** 将共用突击数据转换为页面响应，补充首领、奖励与执刑官下一轮信息。 */
    fun sortie(value: SortieState, now: Long): Sortie {
        val boss = catalog.boss(value.boss)
        val archonIndex = WorldStateCatalog.archons.indexOf(value.boss)
        val nextIndex = (archonIndex + 1) % WorldStateCatalog.archons.size
        return Sortie(
            id = value.id, activation = value.activation.asInstant(), expiry = value.expiry.asInstant(),
            boss = boss.name, faction = boss.faction, eta = remainingTime(value.expiry, now),
            rewardItem = WorldStateCatalog.archonShards.getOrNull(archonIndex)?.let(translations::name),
            nextBoss = if (archonIndex >= 0) catalog.boss(WorldStateCatalog.archons[nextIndex]).name else null,
            nextRewardItem = if (archonIndex >= 0) translations.name(WorldStateCatalog.archonShards[nextIndex]) else null,
            variants = value.missions.map { mission ->
                Variants(
                    missionType = mission.missionType?.let(translations::name),
                    modifierType = mission.modifier?.let(translations::name),
                    node = translations.node(mission.node).name,
                )
            },
        )
    }

    /** 翻译电波赛季与挑战，按 [now] 生成赛季时间文本并保留原有响应结构。 */
    fun nightwave(value: NightwaveState, now: Long): NightWave = NightWave(
        id = "nightwave${value.expiry.asInstant()}",
        activation = value.activation.asInstant(),
        expiry = value.expiry.asInstant(),
        eta = remainingTime(value.expiry, now),
        startTime = remainingTime(value.activation, now),
        tag = value.tag,
        season = value.season,
        phase = value.phase,
        params = value.params,
        possibleChallenges = value.possibleChallenges?.map(::challenge),
        activeChallenges = value.activeChallenges.map(::challenge),
    )

    /** 将官方挑战标识转换为名称、描述与声望值，保留每日、精英和永久标记。 */
    private fun challenge(value: NightwaveChallengeState): Challenges = Challenges(
        id = value.id,
        isDaily = value.daily,
        isElite = value.elite,
        isPermanent = value.permanent,
        title = translations.name(value.key),
        desc = translations.description(value.key) ?: translations.name(value.key),
        reputation = value.reputation,
    )

    /** 翻译入侵的阵营、节点及奖励，沿用共用进度，并在无法估算时间时保留提示文本。 */
    fun invasion(value: InvasionState): Invasions = Invasions(
        id = value.id, activation = value.activation.asInstant(),
        eta = value.remainingMillis?.let { formatDuration(Duration.ofMillis(it)) } ?: "无法估算",
        desc = translations.name(value.description), faction = catalog.faction(value.faction),
        defenderFaction = catalog.faction(value.defenderFaction), node = translations.node(value.node).name,
        count = value.count, requiredRuns = value.goal, completion = value.progress, completed = false,
        vsInfestation = value.infestation, attackerReward = countedRewards(value.attackerReward),
        defenderReward = countedRewards(value.defenderReward),
    )

    /** 把带数量的奖励转换为原有奖励响应，名称通过统一翻译服务取得。 */
    private fun countedRewards(value: RewardState): List<Modifiers> = value.countedItems.map {
        Modifiers(translations.name(it.item), it.count)
    }
}