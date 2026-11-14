package bot.wuliang.riven.range

import bot.wuliang.riven.analysis.*
import org.springframework.stereotype.Service
import kotlin.math.max
import kotlin.math.min

data class RivenRangeAttribute(
    val tag: String,
    val name: String,
    val unit: String,
    val minimum: Double,
    val maximum: Double,
    val factionMultiplier: Boolean,
    val integer: Boolean,
)

data class RivenRangeReport(
    val rivenType: String,
    val typeName: String,
    val disposition: Double,
    val combination: String,
    val combinationLabel: String,
    val positive: List<RivenRangeAttribute>,
    val fusion: List<RivenRangeAttribute>,
    val negative: List<RivenRangeAttribute>,
)

@Service
class RivenRangeService(private val weapons: RivenWeaponCatalog, private val values: RivenValueCatalog) {
    fun resolveType(target: String): String {
        val name = target.lowercase().replace(Regex("\\s+"), "")
        TYPE_ALIASES[name]?.let { return it }
        if (name == "主武器") throw RivenAnalysisException(
            "主武器包含步枪和霰弹枪，两类词条范围不同。请指定武器名，或发送「词条范围 步枪」「词条范围 霰弹枪」。"
        )
        val type = weapons.findText(target).rivenType?.lowercase()
            ?: throw RivenAnalysisException("当前武器目录缺少紫卡类别，请联系管理员更新词库。")
        val canonical = if (type == "arch-gun") "archgun" else type
        if (canonical !in TYPE_NAMES) throw RivenAnalysisException("当前武器的紫卡类别暂不支持范围查询。")
        return canonical
    }

    fun report(type: String, combination: RivenCombination): RivenRangeReport {
        val definitions = values.forType(type)
        fun ranges(positive: Boolean, fused: Boolean): List<RivenRangeAttribute> = definitions.filter {
            (if (positive) it.canBePositive else it.canBeNegative) && RivenFusionAnalysis.isFused(it.tag) == fused
        }.map { definition ->
            val standard = definition.baseValue * RivenValueAnalysis.DISPOSITION *
                    (if (positive) combination.positiveMultiplier else combination.negativeMultiplier)
            RivenRangeAttribute(
                definition.tag, definition.name, definition.unit.symbol,
                min(standard * .9, standard * 1.1), max(standard * .9, standard * 1.1),
                definition.isFactionDamage, definition.unit == RivenValueUnit.NUMBER,
            )
        }
        return RivenRangeReport(
            type, TYPE_NAMES.getValue(type), RivenValueAnalysis.DISPOSITION, combination.notation, combination.label,
            ranges(positive = true, fused = false), ranges(positive = true, fused = true),
            if (combination.negativeCount > 0) ranges(positive = false, fused = false) else emptyList(),
        )
    }

    companion object {
        private val TYPE_NAMES = mapOf(
            "rifle" to "步枪", "shotgun" to "霰弹枪", "pistol" to "手枪", "melee" to "近战",
            "archgun" to "空战枪械", "kitgun" to "组合枪", "zaw" to "自制近战",
        )
        private val TYPE_ALIASES = mapOf(
            "步枪" to "rifle", "步槍" to "rifle", "rifle" to "rifle",
            "霰弹枪" to "shotgun", "霰彈槍" to "shotgun", "散弹枪" to "shotgun", "shotgun" to "shotgun",
            "手枪" to "pistol", "手槍" to "pistol", "副武器" to "pistol", "pistol" to "pistol",
            "近战" to "melee", "近戰" to "melee", "近战武器" to "melee", "melee" to "melee",
            "空战枪械" to "archgun", "空战枪" to "archgun", "archwing枪械" to "archgun",
            "archgun" to "archgun", "arch-gun" to "archgun",
            "组合枪" to "kitgun", "kitgun" to "kitgun",
            "自制近战" to "zaw", "自制近战武器" to "zaw", "zaw" to "zaw",
        )
    }
}