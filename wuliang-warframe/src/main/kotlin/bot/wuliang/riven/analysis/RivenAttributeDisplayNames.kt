package bot.wuliang.riven.analysis

/** 报告词条显示名的人工覆盖配置，不参与输入识别或决定数值词条池。 */
object RivenAttributeDisplayNames {
    private val namesByGameRef = mapOf(
        "WeaponCritChanceMod" to "暴击几率",
        "WeaponCritDamageMod" to "暴击伤害",
        "WeaponDamageAmountMod" to "基础伤害",
        "WeaponMeleeDamageMod" to "近战伤害",
        "WeaponFireIterationsMod" to "多重射击",
        "WeaponFireRateMod" to "射速",
        "WeaponElectricityDamageMod" to "电击伤害",
        "WeaponFireDamageMod" to "火焰伤害",
        "WeaponFreezeDamageMod" to "冰冻伤害",
        "WeaponToxinDamageMod" to "毒素伤害",
        "WeaponImpactDamageMod" to "冲击伤害",
        "WeaponArmorPiercingDamageMod" to "穿刺伤害",
        "WeaponSlashDamageMod" to "切割伤害",
        "WeaponStunChanceMod" to "触发几率",
        "WeaponProcTimeMod" to "触发时间",
        "WeaponAmmoMaxMod" to "弹药最大值",
        "WeaponClipMaxMod" to "弹匣容量",
        "WeaponProjectileSpeedMod" to "投射物速度",
        "WeaponPunctureDepthMod" to "穿透",
        "WeaponRecoilReductionMod" to "后坐力",
        "WeaponReloadSpeedMod" to "装填速度",
        "WeaponZoomFovMod" to "变焦",
        "ComboDurationMod" to "连击持续时间",
        "SlideAttackCritChanceMod" to "滑暴几率",
        "WeaponMeleeRangeIncMod" to "攻击范围",
        "WeaponMeleeFinisherDamageMod" to "处决伤害",
        "WeaponMeleeComboEfficiencyMod" to "重击效率",
        "WeaponMeleeComboInitialBonusMod" to "初始连击",
        "WeaponMeleeComboPointsOnHitMod" to "几率不获得连击数",
        "WeaponMeleeComboBonusOnHitMod" to "额外连击数",
    ) + listOf("Corpus", "Grineer", "Infested").flatMap { faction ->
        listOf("WeaponFactionDamage$faction", "WeaponMeleeFactionDamage$faction").map {
            it to "对${faction}伤害"
        }
    }.toMap()

    /** 返回报告显示名；未配置时返回 null，由数值词库使用官方名称。 */
    fun nameFor(gameRef: String): String? = namesByGameRef[gameRef]
}