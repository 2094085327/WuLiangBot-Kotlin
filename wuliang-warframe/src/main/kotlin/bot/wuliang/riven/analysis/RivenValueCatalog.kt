package bot.wuliang.riven.analysis

import bot.wuliang.riven.RivenAttributeAliases
import bot.wuliang.translation.PublicExportIndex
import bot.wuliang.translation.PublicExportService
import org.springframework.stereotype.Component
import kotlin.math.sign

enum class RivenValueUnit(val symbol: String, val scale: Double) {
    PERCENT("%", 100.0), METRES("米", 1.0), SECONDS("秒", 1.0), NUMBER("", 1.0),
}

data class RivenValueDefinition(
    val tag: String,
    val name: String,
    val aliases: Set<String>,
    val rawValue: Double,
    val unit: RivenValueUnit,
    val canBePositive: Boolean,
    val canBeNegative: Boolean,
    val reverseValueSymbol: Boolean,
) {
    val isFactionDamage: Boolean
        get() = tag.startsWith("WeaponFactionDamage") || tag.startsWith("WeaponMeleeFactionDamage") ||
                RivenFusionAnalysis.isFaction(tag)
    val positiveSign: Double get() = rawValue.sign * if (reverseValueSymbol) -1 else 1

    /** Wiki 满级基础值 */
    val baseValue: Double get() = rawValue * 90 * unit.scale * if (reverseValueSymbol) -1 else 1
}

class RivenAnalysisException(message: String) : RuntimeException(message)

/** 从 Public Export 读取数值和合法词条池 */
@Component
class RivenValueCatalog(private val translations: PublicExportService) {
    @Volatile
    private var cached: Snapshot? = null

    private data class Snapshot(val index: PublicExportIndex, val types: Map<String, List<RivenValueDefinition>>)

    fun forType(type: String): List<RivenValueDefinition> {
        val index = translations.index()
        val snapshot = cached?.takeIf { it.index === index } ?: synchronized(this) {
            cached?.takeIf { it.index === index } ?: Snapshot(index, TYPES.mapValues { (_, mod) ->
                val entries = index.metadata("/Lotus/Upgrades/Mods/Randomized/$mod")?.get("upgradeEntries")
                entries?.mapNotNull { entry ->
                    val tag = entry["tag"]?.asText() ?: return@mapNotNull null
                    val value = entry["upgradeValues"]?.firstOrNull() ?: return@mapNotNull null
                    val raw = value["value"]?.asDouble()?.takeIf { it.isFinite() && it != 0.0 }
                        ?: return@mapNotNull null
                    val locTag = value["locTag"]?.asText()
                    val labels = listOfNotNull(
                        locTag?.let { index.field(it, "name", "zh") },
                        locTag?.let { index.field(it, "name", "en") }).map(::cleanLabel).filter(String::isNotBlank)
                    val aliases = (labels + RivenAttributeAliases.namesForGameRef(tag)).toSet()
                    if (aliases.isEmpty()) return@mapNotNull null
                    RivenValueDefinition(
                        tag, RivenAttributeDisplayNames.nameFor(tag) ?: aliases.first(), aliases, raw,
                        when (tag) {
                            "WeaponPunctureDepthMod", "WeaponMeleeRangeIncMod" -> RivenValueUnit.METRES
                            "ComboDurationMod" -> RivenValueUnit.SECONDS
                            "WeaponMeleeComboInitialBonusMod" -> RivenValueUnit.NUMBER
                            else -> RivenValueUnit.PERCENT
                        },
                        entry["canBeBuff"]?.asBoolean() ?: !entry.path("prefixTag").asText().isBlank(),
                        entry["canBeCurse"]?.asBoolean()
                            ?: (tag !in POSITIVE_ONLY && !RivenFusionAnalysis.isFused(tag)),
                        value.path("reverseValueSymbol").asBoolean(false),
                    )
                }.orEmpty()
            }).also { cached = it }
        }
        return snapshot.types[type.lowercase()]?.takeIf { it.isNotEmpty() }
            ?: throw RivenAnalysisException("当前词库缺少该类紫卡的数值参数，请联系管理员更新词库。")
    }

    companion object {
        val TYPES = mapOf(
            "rifle" to "LotusRifleRandomModRare", "shotgun" to "LotusShotgunRandomModRare",
            "pistol" to "LotusPistolRandomModRare", "melee" to "PlayerMeleeWeaponRandomModRare",
            "archgun" to "LotusArchgunRandomModRare", "arch-gun" to "LotusArchgunRandomModRare",
            "kitgun" to "LotusModularPistolRandomModRare", "zaw" to "LotusModularMeleeRandomModRare",
        )
        private val POSITIVE_ONLY = setOf(
            "WeaponElectricityDamageMod", "WeaponFireDamageMod",
            "WeaponFreezeDamageMod", "WeaponToxinDamageMod", "WeaponPunctureDepthMod", "WeaponMeleeComboBonusOnHitMod",
            "WeaponMeleeComboInitialBonusMod"
        )

        private fun cleanLabel(value: String): String = PublicExportIndex.clean(value)
            .replace(Regex("\\|[^|]+\\||[%％]|[（(].*?[）)]|<[^>]+>"), "")
            .trim().trimStart('+', '-').trim()
    }
}