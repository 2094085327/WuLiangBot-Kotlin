package bot.wuliang.worldstate.presentation

import bot.wuliang.translation.WorldStateCatalog
import bot.wuliang.worldstate.model.WorldEntry
import bot.wuliang.worldstate.model.WorldSection
import bot.wuliang.worldstate.parser.FissureParser
import bot.wuliang.worldstate.parser.InvasionParser
import bot.wuliang.worldstate.parser.NightwaveParser
import bot.wuliang.worldstate.parser.SortieParser
import com.fasterxml.jackson.databind.JsonNode

/** 面板的任务板块只负责排版，共用解析器负责筛选与解释官方记录。 */
internal class MissionSections(private val display: WorldStateDisplay) {
    /** 组装普通、钢铁之路和九重天裂缝板块，按结束时间排序。 */
    fun fissures(root: JsonNode, now: Long): WorldSection {
        val records = FissureParser.parse(root.path("ActiveMissions"), false, now) +
                FissureParser.parse(root.path("VoidStorms"), true, now)
        val entries = records.map { value ->
            val label = mapOf(
                "VoidT1" to "古纪", "VoidT2" to "前纪", "VoidT3" to "中纪", "VoidT4" to "后纪",
                "VoidT5" to "安魂", "VoidT6" to "全能"
            )[value.tier] ?: value.tier
            WorldEntry(
                value.id,
                display.node(value.node),
                if (value.storm) "九重天" else if (value.hard) "钢铁之路" else "普通",
                "$label · ${value.missionType?.let(display::name) ?: display.mission(value.node)}",
                activation = value.activation,
                expiry = value.expiry,
            )
        }.sortedBy { it.expiry }
        return WorldSection("fissures", "虚空裂缝", entries)
    }

    /** 把当前有效电波挑战转换为面板条目，展示名称、类别、描述和时间边界。 */
    fun nightwave(root: JsonNode, now: Long): WorldSection = WorldSection(
        "nightwave", "午夜电波",
        NightwaveParser.parse(root.path("SeasonInfo"), now).activeChallenges.map { value ->
            WorldEntry(
                value.id, display.name(value.key), if (value.daily) "每日" else if (value.elite) "精英每周" else "每周",
                details = listOf(display.description(value.key)), activation = value.activation, expiry = value.expiry,
            )
        },
    )

    /** 组装每日突击与执刑官猎杀的任务条目，展示首领及任务附加条件。 */
    fun sorties(root: JsonNode, now: Long): WorldSection = WorldSection(
        "sorties", "突击与执刑官",
        listOf("Sorties", "LiteSorties").flatMap { field ->
            val sortie = SortieParser.parse(root.path(field), if (field == "Sorties") "Variants" else "Missions", now)
                ?: return@flatMap emptyList()
            sortie.missions.mapIndexed { index, mission ->
                WorldEntry(
                    "$field-$index", display.node(mission.node), if (field == "Sorties") "每日突击" else "执刑官猎杀",
                    "任务 ${index + 1} · ${display.name(mission.missionType.orEmpty())}",
                    details = listOf(display.name(WorldStateCatalog.bosses[sortie.boss]?.first ?: sortie.boss)) +
                            mission.modifier?.let { listOf(display.name(it), display.description(it)) }.orEmpty(),
                    activation = sortie.activation, expiry = sortie.expiry,
                )
            }
        },
    )

    /** 组装未结束的入侵条目，展示共用进度及双方奖励。 */
    fun invasions(root: JsonNode, now: Long): WorldSection = WorldSection(
        "invasions", "入侵",
        InvasionParser.parse(root.path("Invasions"), now).mapIndexed { index, value ->
            WorldEntry(
                "invasion-$index", display.node(value.node), if (value.infestation) "感染爆发" else "阵营入侵",
                "${display.name(value.faction)} / ${display.name(value.defenderFaction)}",
                details = listOf(
                    "进攻方：${display.reward(value.attackerReward).joinToString("、").ifEmpty { "无" }}",
                    "防守方：${display.reward(value.defenderReward).joinToString("、").ifEmpty { "无" }}",
                ),
                progress = value.progress,
            )
        },
    )
}