package bot.wuliang.riven.analysis

import bot.wuliang.riven.samples.RivenMarketReference
import org.springframework.stereotype.Component
import kotlin.math.max
import kotlin.math.min

/**
 * 图片或文字解析得到的单条词条，供等级反推和理论数值计算使用。
 * 词条正负面与数值符号分开保存，例如减后坐力是正面词条。
 */
data class ParsedRivenAttribute(
    val definition: RivenValueDefinition, // 当前武器类别中的词条定义，包含基础值、单位和合法方向。
    val value: Double, // 用于计算的带符号数值；145.9%记为145.9，阵营倍率先换算为百分比增量。
    val positive: Boolean, // 是否为正面词条，不能直接按数值是否大于0判断。
    val cardDisplayValue: String? = null, // 解析后保留的显示数值，含符号和单位；null时由展示端格式化。
    val roundingTolerance: Double? = null, // 等级合法性判断的绝对容差，与value同单位；null时使用单位默认容差。
)

/**
 * 进入数值分析前的整张紫卡解析结果，供图片和文字入口共用。
 * 只保存解析信息，不包含理论范围、市场参考或融合预测。
 */
data class ParsedRiven(
    val weaponName: String, // 匹配后的武器显示名称。
    val weaponSlug: String, // WM武器标识，用于查询和市场参考。
    val rivenType: String, // 数值词库使用的紫卡类别，如rifle、melee。
    val rank: Int?, // 用户指定的0至8级；null时由全部词条数值反推。
    val attributes: List<ParsedRivenAttribute>, // 按解析顺序保存的全部词条，包含已识别的融合词条。
    val warnings: List<String>, // 解析阶段的校正、置信度和卡面取整等提示。
    val suffix: String? = null, // 从卡面标题识别的拉丁后缀；未识别时为null。
    val valuesNeedReview: Boolean = false, // 内部数值待核对标记，用于阻止输出等级候选、理论范围和评级。
)

/**
 * 单条词条在某个候选等级下的理论范围和实际随机浮动。
 * 偏差和位置均按实际值与标准值的比值计算，不因负面词条而反向调整。
 */
data class RivenAttributeRange(
    val rank: Int, // 当前范围对应的紫卡等级，取值0至8。
    val minimum: Double, // 带符号的理论下限，取标准值0.9倍与1.1倍中的较小值。
    val maximum: Double, // 带符号的理论上限，取标准值0.9倍与1.1倍中的较大值。
    val standard: Double, // 该等级的标准值，包含固定倾向和正负词条组合修正，不含随机浮动。
    val deviation: Double, // 相对标准值的偏差百分比，计算为(value / standard - 1) × 100。
    val rollPosition: Double, // 随机倍率在0.9至1.1区间中的位置，限制为0至100，不代表数值评级。
)

/** 单条词条的分析结果；等级未确认时可分别保留多个范围，数值待核对时范围为空。 */
data class RivenAnalyzedAttribute(
    val tag: String, // 官方数值词条标识game_ref，不能直接当作WM词条标识。
    val name: String, // 数值词库中的词条显示名称。
    val value: Double, // 用于计算的带符号数值，沿用解析结果；百分比按145.9而不是1.459保存。
    val unit: String, // 显示单位：%、米、秒；整数类词条为空字符串。
    val positive: Boolean, // 是否为正面词条，与卡面加减号分开判断。
    val baseValue: Double, // 满级基础值，不含倾向、词条组合和随机浮动修正。
    val ranges: List<RivenAttributeRange>, // 各合法候选等级的理论范围；数值待核对时为空。
    val cardDisplayValue: String? = null, // 保留的显示数值，如阵营倍率或文字输入；null时使用value和unit。
)

/**
 * 供分析图片展示的整张紫卡报告，汇总数值结果、提示、市场参考和融合预测。
 * 等级只有一个合法候选时才确定；数值待核对时保留词条，但不提供范围、浮动或评级。
 */
data class RivenAnalysisReport(
    val weaponName: String, // 武器显示名称。
    val weaponSlug: String, // WM武器标识，用于后续市场参考查询。
    val rivenType: String, // 本次使用的紫卡数值词库类别。
    val disposition: Double, // 本次计算采用的倾向，当前固定为1.2，不是武器的实时倾向。
    val rank: Int?, // 唯一合法的紫卡等级；无合法候选或存在多个候选时为null。
    val rankCandidates: List<Int>, // 满足全部词条数值和指定等级约束的候选；数值待核对时为空。
    val positiveCount: Int, // 正面词条数量，包含已有的融合词条。
    val negativeCount: Int, // 负面词条数量。
    val positiveMultiplier: Double, // 根据正负词条数量选取的正面组合系数。
    val negativeMultiplier: Double, // 负面组合系数，存在负面词条时为负值，无负面时为0。
    val attributes: List<RivenAnalyzedAttribute>, // 各词条的输入数值和候选等级范围。
    val warnings: List<String>, // 解析提示与等级反推、数值待核对等分析提示。
    val generatedAt: Long, // 报告生成时间，单位为毫秒时间戳。
    val suffix: String? = null, // 从卡面标题识别的拉丁后缀；未识别时为null。
    val marketReference: RivenMarketReference? = null, // 由命令流程后续补入的市场参考；尚未补入时为null。
    val fusionAnalysis: List<RivenFusionGroup> = emptyList(), // 当前卡面的可融合或可锁定预测，无可展示结果时为空。
)

@Component
class RivenValueAnalysis(private val values: RivenValueCatalog) {
    fun analyze(riven: ParsedRiven): RivenAnalysisReport = analyze(riven, allowInvalidValues = false)

    /** 图片识别保留已确认的词条；数值无法校准时返回待核对报告。 */
    fun analyzeOcr(riven: ParsedRiven): RivenAnalysisReport = analyze(riven, allowInvalidValues = true)

    /** 文字模式保留名称、方向与输入值；没有共同合法等级时不生成数值范围和浮动。 */
    fun analyzeText(riven: ParsedRiven): RivenAnalysisReport = analyze(riven, allowInvalidValues = true)

    private fun analyze(riven: ParsedRiven, allowInvalidValues: Boolean): RivenAnalysisReport {
        val positives = riven.attributes.count { it.positive }
        val negatives = riven.attributes.size - positives
        if (riven.attributes.count { RivenFusionAnalysis.isFused(it.definition.tag) } > 1) {
            throw RivenAnalysisException("一张紫卡只能有一条融合词条，请检查输入。")
        }
        val multipliers = when (positives to negatives) {
            2 to 0 -> 0.99 to 0.0
            2 to 1 -> 1.2375 to -0.495
            3 to 0 -> 0.75 to 0.0
            3 to 1 -> 0.9375 to -0.75
            else -> throw RivenAnalysisException("未识别到完整紫卡：应为两条或三条正面词条，至多一条负面词条。请重新截图后重试。")
        }
        val allRanges = riven.attributes.map { attribute ->
            (0..8).map { rank ->
                val multiplier = if (attribute.positive) multipliers.first else multipliers.second
                val standard = attribute.definition.baseValue * DISPOSITION * multiplier * (rank + 1) / 9
                val deviation = (attribute.value / standard - 1) * 100
                RivenAttributeRange(
                    rank, min(standard * .9, standard * 1.1), max(standard * .9, standard * 1.1),
                    standard, deviation, ((attribute.value / standard - .9) / .2 * 100).coerceIn(0.0, 100.0)
                )
            }
        }
        // 卡面经过取整：百分比/米/秒通常一位小数，初始连击按整数留容差。
        val possible = (0..8).filter { rank ->
            riven.attributes.indices.all { i ->
                val range = allRanges[i][rank]
                val tolerance = riven.attributes[i].roundingTolerance
                    ?: if (riven.attributes[i].definition.unit == RivenValueUnit.NUMBER) .501 else .051
                riven.attributes[i].value >= range.minimum - tolerance && riven.attributes[i].value <= range.maximum + tolerance
            }
        }
        val ranks = when {
            riven.valuesNeedReview -> emptyList()
            riven.rank != null -> possible.filter { it == riven.rank }
            else -> possible
        }
        if (ranks.isEmpty() && !allowInvalidValues) throw RivenAnalysisException(
            "识别数值与紫卡数值范围不符。请检查正负号、小数点、等级，并发送包含全部词条的单张紫卡图片。"
        )
        val warnings = riven.warnings.toMutableList()
        if (ranks.isEmpty()) {
            warnings += "部分数值待核对，暂不计算理论范围、浮动和数值评级。"
        } else if (riven.rank == null && ranks.size > 1) {
            warnings += "卡面等级无法确定，数值可能对应 ${ranks.joinToString("、")} 级，已分别列出范围。可使用「紫卡分析 等级8」指定实际等级。"
        } else if (riven.rank == null) warnings += "等级根据全部词条数值推算为 ${ranks.single()} 级。"
        if (riven.attributes.any { it.definition.unit == RivenValueUnit.NUMBER }) warnings += "初始连击等整数显示词条的浮动位置受卡面取整影响较大。"
        return RivenAnalysisReport(
            riven.weaponName, riven.weaponSlug, riven.rivenType, DISPOSITION, ranks.singleOrNull(), ranks,
            positives, negatives, multipliers.first, multipliers.second,
            riven.attributes.mapIndexed { i, attribute ->
                RivenAnalyzedAttribute(
                    attribute.definition.tag, attribute.definition.name, attribute.value,
                    attribute.definition.unit.symbol, attribute.positive, attribute.definition.baseValue,
                    ranks.map { allRanges[i][it] }, attribute.cardDisplayValue
                )
            },
            warnings, System.currentTimeMillis(), riven.suffix,
        ).let { report ->
            report.copy(
                fusionAnalysis = RivenFusionAnalysis.analyze(
                    report,
                    values.forType(riven.rivenType)
                )
            )
        }
    }

    companion object {
        const val DISPOSITION = 1.2
    }
}