package bot.wuliang.riven.analysis

import bot.wuliang.riven.analysis.RivenFusionAnalysis.analyze


/**
 * 配方直接使用官方属性标识，融合词条集合从配方结果生成。
 */
object RivenFusionAnalysis {
    private val ranged = setOf("rifle", "shotgun", "pistol", "kitgun", "archgun", "arch-gun")
    private val melee = setOf("melee", "zaw")

    private data class Recipe(val first: String, val second: String, val result: String)

    /**
     * 按紫卡类别生成专属配方和通用配方。
     *
     * 区分远程与近战材料，并为 Archgun 排除收起时装填配方。
     * 材料和结果均使用官方属性标识，是否存在于当前词条池由 [analyze] 检查。
     *
     * @param type 受支持的小写紫卡类别，如 `rifle`、`melee` 或 `archgun`。
     * @return 当前类别的候选配方。
     */
    private fun recipes(type: String): List<Recipe> {
        val isMelee = type in melee
        val faction = if (isMelee) "WeaponMeleeFactionDamage" else "WeaponFactionDamage"
        val damage = if (isMelee) "WeaponMeleeDamageMod" else "WeaponDamageAmountMod"
        val specific = if (isMelee) listOf(
            Recipe("WeaponMeleeComboEfficiencyMod", "WeaponMeleeComboBonusOnHitMod", "WeaponMeleeHeavyAttackDamageMod"),
            Recipe("WeaponMeleeComboEfficiencyMod", "ComboDurationMod", "WeaponMeleeHeavyAttackChargeMod"),
            Recipe("WeaponFireRateMod", "WeaponMeleeRangeIncMod", "WeaponMeleeParryAngleMod"),
            Recipe("WeaponFireRateMod", damage, "WeaponMeleeSlamDamageMod"),
        ) else listOf(
            Recipe(damage, "WeaponZoomFovMod", "WeaponWeakpointDamage"),
            Recipe(damage, "WeaponFireIterationsMod", "WeaponWeakpointDamage"),
            Recipe("WeaponCritChanceMod", "WeaponZoomFovMod", "WeaponWeakpointCriticalChance"),
            Recipe("WeaponCritChanceMod", "WeaponFireIterationsMod", "WeaponWeakpointCriticalChance"),
            Recipe("WeaponClipMaxMod", "WeaponReloadSpeedMod", "WeaponAmmoEfficiency"),
            Recipe("WeaponAmmoMaxMod", "WeaponRecoilReductionMod", "WeaponAmmoEfficiency"),
        ) + if (type in setOf("archgun", "arch-gun")) emptyList() else listOf(
            Recipe("WeaponAmmoMaxMod", "WeaponReloadSpeedMod", "WeaponMagazineReloadHolstered"),
            Recipe("WeaponAmmoMaxMod", "WeaponClipMaxMod", "WeaponMagazineReloadHolstered"),
        )
        return specific + listOf(
            Recipe(damage, "WeaponStunChanceMod", "WeaponStatusDamage"),
            Recipe("WeaponToxinDamageMod", "WeaponElectricityDamageMod", "WeaponCorrosiveDamageMod"),
            Recipe("WeaponToxinDamageMod", "WeaponFreezeDamageMod", "WeaponViralDamageMod"),
            Recipe("WeaponToxinDamageMod", "WeaponFireDamageMod", "WeaponGasDamageMod"),
            Recipe("WeaponFireDamageMod", "WeaponFreezeDamageMod", "WeaponBlastDamageMod"),
            Recipe("WeaponFireDamageMod", "WeaponElectricityDamageMod", "WeaponRadiationDamageMod"),
            Recipe("WeaponElectricityDamageMod", "WeaponFreezeDamageMod", "WeaponMagneticDamageMod"),
            Recipe("${faction}Grineer", "${faction}Corpus", "WeaponFactionDamageOrokin"),
            Recipe("${faction}Grineer", "${faction}Infested", "WeaponFactionDamageScaldra"),
            Recipe("${faction}Corpus", "${faction}Infested", "WeaponFactionDamageTechrot"),
        )
    }

    private val fusionTags = (ranged + melee).flatMap(::recipes).mapTo(hashSetOf()) { it.result }

    /**
     * 判断属性是否为任一融合配方的结果。
     *
     * @param tag Public Export 属性标识，对应属性表中的 `game_ref`。
     * @return 属于融合词条时返回 `true`，否则返回 `false`。
     */
    fun isFused(tag: String) = tag in fusionTags

    /**
     * 判断属性是否为融合配方中的阵营伤害词条。
     *
     * @param tag Public Export 属性标识，对应属性表中的 `game_ref`。
     * @return 同时属于融合词条和阵营伤害时返回 `true`，否则返回 `false`。
     */
    fun isFaction(tag: String) = isFused(tag) && tag.startsWith("WeaponFactionDamage")

    /**
     * 根据已有紫卡报告，生成可融合或可锁定的选项及数值预测。
     *
     * 两条材料和目标属性都必须存在于当前类别的词条池。材料齐全时标记为 `FUSIBLE`，
     * 仅有一条时标记为 `LOCKABLE`，并列出缺少的材料。已有融合词条或类别不受支持时返回空列表。
     *
     * 按报告的候选等级预测，继承已有材料相对标准值的较大倍率；负面材料保留原始符号。
     * 某等级缺少材料范围时跳过该等级的预测，选项仍可保留。
     *
     * @param report 已完成数值分析的报告，包含现有词条、候选等级和组合修正。
     * @param definitions 当前紫卡类别的可用词条定义，包含材料和融合结果的数值参数。
     * @return 按已有材料分组的融合选项，可融合组优先；没有可用选项时返回空列表。
     */
    fun analyze(report: RivenAnalysisReport, definitions: List<RivenValueDefinition>): List<RivenFusionGroup> {
        if (report.attributes.any { isFused(it.tag) }) return emptyList()
        val type = report.rivenType.lowercase()
        if (type !in ranged && type !in melee) return emptyList()
        val available = definitions.associateBy { it.tag }
        val present = report.attributes.associateBy { it.tag }
        val groups = linkedMapOf<List<String>, MutableList<RivenFusionOption>>()
        for (recipe in recipes(type)) {
            // 两个材料都必须在当前类别的词条池中
            if (recipe.first !in available || recipe.second !in available) continue
            val sources = listOfNotNull(present[recipe.first], present[recipe.second])
            if (sources.isEmpty()) continue
            val target = available[recipe.result] ?: continue
            val missing = if (sources.size == 1) available.getValue(
                if (sources.single().tag == recipe.first) recipe.second else recipe.first
            ) else null
            val ranges = report.rankCandidates.mapNotNull { rank ->
                val sourceRanges = sources.map { source -> source.ranges.singleOrNull { it.rank == rank } }
                if (sourceRanges.any { it == null }) return@mapNotNull null
                val scale = report.disposition * report.positiveMultiplier * (rank + 1) / 9
                val targetStandard = target.baseValue * scale
                // 固定卡面输入，不读取deviation，也不截断卡面取整后的实际值。
                // 不同属性先按各自标准值换成可比倍率。负面数值和标准值均带原始符号。
                val knownFactor = sources.indices.maxOf { sources[it].value / sourceRanges[it]!!.standard }
                // 缺少材料时按已有材料倍率估算，不假设新材料恰好达到标准值。
                val value = targetStandard * knownFactor
                // 量条展示融合属性的完整理论范围，不是缺少材料带来的预测区间。
                RivenFusionRange(
                    rank, value, targetStandard * .9, targetStandard * 1.1,
                    targetStandard, (knownFactor - 1) * 100,
                    ((knownFactor - .9) / .2 * 100).coerceIn(0.0, 100.0)
                )
            }
            groups.getOrPut(sources.map { it.tag }) { mutableListOf() } +=
                RivenFusionOption(target.tag, target.name, missing?.name, isFaction(target.tag), ranges)
        }
        return groups.map { (tags, options) ->
            RivenFusionGroup(
                if (tags.size == 2) "FUSIBLE" else "LOCKABLE",
                tags.map {
                    present.getValue(it).let { source -> RivenFusionSource(source.tag, source.name, source.positive) }
                },
                options
            )
        }.sortedBy { if (it.status == "FUSIBLE") 0 else 1 }
    }
}

data class RivenFusionSource(val tag: String, val name: String, val positive: Boolean)
data class RivenFusionRange(
    val rank: Int, val value: Double, val minimum: Double, val maximum: Double,
    val standard: Double, val deviation: Double, val rollPosition: Double
)

data class RivenFusionOption(
    val tag: String, val name: String, val requiredAttribute: String?,
    val factionMultiplier: Boolean, val ranges: List<RivenFusionRange>
)

data class RivenFusionGroup(
    val status: String,
    val sources: List<RivenFusionSource>,
    val options: List<RivenFusionOption>
)