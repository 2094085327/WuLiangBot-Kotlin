package bot.wuliang.translation

import bot.wuliang.config.WARFRAME_SYNTHESIS_LOCATIONS
import bot.wuliang.moudles.Boss
import bot.wuliang.moudles.Modifiers
import bot.wuliang.moudles.SimarisLocation
import bot.wuliang.moudles.SimarisPersistent
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.stereotype.Component
import java.io.File

/** 维护世界状态代码间的关联和社区补充的玩法数据，展示文本统一交给翻译服务。 */
@Component
class WorldStateCatalog(private val translations: PublicExportService) {
    private val synthesis = jacksonObjectMapper().readTree(File(WARFRAME_SYNTHESIS_LOCATIONS))

    data class SteelReward(val item: String, val cost: Int, val count: Int = 1)

    val steelRewards = listOf(
        SteelReward("/Lotus/Types/Recipes/Components/UmbraFormaBlueprint", 150),
        SteelReward("/Lotus/Types/Items/MiscItems/Kuva", 55, 50000),
        SteelReward("/Lotus/Language/Omega/OmegaKitgunMod", 75),
        SteelReward("/Lotus/Language/Items/Forma", 75, 3),
        SteelReward("/Lotus/Language/Omega/OmegaZawMod", 75),
        SteelReward("/Lotus/Language/Items/FusionBundle", 150, 30000),
        SteelReward("/Lotus/Language/Omega/OmegaRifleMod", 75),
        SteelReward("/Lotus/Language/Omega/OmegaShotgunMod", 75),
    )

    fun steelName(reward: SteelReward): String =
        (if (reward.count == 1) "" else "${reward.count} × ") + translations.name(reward.item)

    /**
     * 将突击首领代码关联到游戏本地化键和派系；未知代码保留统一翻译入口的回退结果。
     */
    fun boss(code: String): Boss {
        val rule = bosses[code]
        return Boss(name = translations.name(rule?.first ?: code), faction = rule?.second?.let(::faction))
    }

    /**
     * 把 VoidT 等级代码关联到遗物纪元，并保留数字等级用于排序。
     */
    fun tier(code: String): Modifiers {
        val number = code.removePrefix("VoidT").toIntOrNull()
        val era = listOf("LITH", "MESO", "NEO", "AXI", "REQUIEM", "OMNI").getOrNull((number ?: 0) - 1)
        return Modifiers(value = translations.name(era?.let { "/Lotus/Language/Relics/Era_$it" } ?: code),
            num = number ?: 0)
    }

    fun faction(code: String): String = translations.faction(code)

    /**
     * 组合结合仪式目标与社区刷新地点，优先使用 Plus 节点元数据补全地点译名。
     * 未收录的目标返回 null，由调用方决定展示方式。
     */
    fun synthesisTarget(id: String): SimarisPersistent? {
        val imageKey = researchTargets[PublicExportIndex.normalize(id)] ?: return null
        val row = synthesis.firstOrNull { it["imageKey"].asText() == imageKey } ?: return null
        val nameKey = targetNames.getValue(imageKey)
        return SimarisPersistent(
            imageKey = imageKey,
            name = translations.name(nameKey),
            locations = row["locations"].map { location ->
                val node = location["nodeId"]?.asText()?.let { translations.index().metadata(it) }
                SimarisLocation(
                    level = location["level"].asText(),
                    spawnRate = location["spawnRate"].asText(),
                    mission = (node?.get("name")?.asText() ?: location["missionKey"].asText()).let(translations::name),
                    planet = (node?.get("systemName")?.asText()
                        ?: location["planetKey"].asText()).let(translations::name),
                    faction = faction(node?.get("faction")?.asText() ?: location["factionKey"].asText()),
                    type = (node?.get("missionType")?.asText() ?: location["typeKey"].asText()).let(translations::name),
                )
            }
        )
    }

    companion object {
        private fun key(suffix: String) = "/Lotus/Language/$suffix"
        val bosses = mapOf(
            "HYENA" to ("Bosses/BossTheHyena" to "FC_CORPUS"),
            "KELA" to ("Game/KelaDeThaym" to "FC_GRINEER"),
            "VOR" to ("Game/CaptainVor" to "FC_GRINEER"),
            "RUK" to ("Game/SargasRuk" to "FC_GRINEER"),
            "HEK" to ("Game/CouncilorVayHek" to "FC_GRINEER"),
            "KRIL" to ("Game/LieutenantLechKril" to "FC_GRINEER"),
            "TYL" to ("Game/TylRegorName" to "FC_GRINEER"),
            "JACKAL" to ("Game/QuadRobot" to "FC_CORPUS"),
            "ALAD" to ("Game/AladV" to "FC_CORPUS"),
            "AMBULAS" to ("Game/BossAmbulas" to "FC_CORPUS"),
            "RAPTOR" to ("Bosses/BossTheRaptor" to "FC_CORPUS"),
            "PHORID" to ("Game/Phorid" to "FC_INFESTATION"),
            "LEPHANTIS" to ("Game/GolemBossFull" to "FC_INFESTATION"),
            "INFALAD" to ("Game/InfestedAladV" to "FC_INFESTATION"),
            "CORRUPTED_VOR" to ("Game/VorTwo" to "FC_OROKIN"),
            "BOREAL" to ("Narmer/ArchonBoreal" to "FC_NARMER"),
            "AMAR" to ("Narmer/ArchonAmar" to "FC_NARMER"),
            "NIRA" to ("Narmer/ArchonNira" to "FC_NARMER"),
            "NEF" to ("Bosses/NefAnyoName" to "FC_CORPUS"),
        ).mapKeys { "SORTIE_BOSS_${it.key}" }.mapValues { key(it.value.first) to it.value.second }

        val archons = listOf("SORTIE_BOSS_AMAR", "SORTIE_BOSS_NIRA", "SORTIE_BOSS_BOREAL")
        val archonShards = listOf(
            "ArchonCrystalAmarName",
            "ArchonCrystalNiraName",
            "ArchonCrystalBorealName"
        ).map { key("Narmer/$it") }
        val birthdays =
            mapOf(45 to "Lettie", 143 to "Jabir", 191 to "Aoi", 306 to "Eleanor", 307 to "Arthur", 338 to "Quincy")
                .mapValues { key("1999/Messenger${it.value}Name") }
        private val researchTargets =
            listOf("lancer", "anti_moa", "arid_eviscerator", "corrupted_ancient", "crewman", "runner", "guardsman")
                .mapIndexed { index, target -> "/lotus/types/game/library/targets/research${index + 1}target" to target }
                .toMap()
        private val targetNames = mapOf(
            "lancer" to key("Game/GrineerLancerRifle"),
            "anti_moa" to key("Game/LaserDiscBiped"),
            "arid_eviscerator" to key("Game/GrineerEvisceratorLancerDesert"),
            "corrupted_ancient" to key("Game/CorruptedAncient"),
            "crewman" to key("Game/RifleSpaceMan"),
            "runner" to key("Game/InfestedRunner"),
            "guardsman" to key("EnemyLeaders/ProsecutorGenericName"),
        )
    }
}
