package bot.wuliang.riven

import java.text.Normalizer
import java.util.*

/** 紫卡查询、文字分析和 OCR 共用的人工别名；报告显示名在 RivenAttributeDisplayNames 单独配置。 */
object RivenAttributeAliases {
    private val slugsByGameRef = mapOf(
        "WeaponCritChanceMod" to "critical_chance",
        "WeaponCritDamageMod" to "critical_damage",
        "WeaponDamageAmountMod" to "base_damage_/_melee_damage",
        "WeaponMeleeDamageMod" to "base_damage_/_melee_damage",
        "WeaponFireRateMod" to "fire_rate_/_attack_speed",
        "WeaponFireIterationsMod" to "multishot",
        "WeaponToxinDamageMod" to "toxin_damage",
        "WeaponFreezeDamageMod" to "cold_damage",
        "WeaponFireDamageMod" to "heat_damage",
        "WeaponElectricityDamageMod" to "electric_damage",
        "WeaponImpactDamageMod" to "impact_damage",
        "WeaponArmorPiercingDamageMod" to "puncture_damage",
        "WeaponSlashDamageMod" to "slash_damage",
        "WeaponStunChanceMod" to "status_chance",
        "WeaponProcTimeMod" to "status_duration",
        "WeaponProjectileSpeedMod" to "projectile_speed",
        "WeaponReloadSpeedMod" to "reload_speed",
        "WeaponClipMaxMod" to "magazine_capacity",
        "WeaponAmmoMaxMod" to "ammo_maximum",
        "WeaponPunctureDepthMod" to "punch_through",
        "WeaponMeleeRangeIncMod" to "range",
        "WeaponRecoilReductionMod" to "recoil",
        "WeaponZoomFovMod" to "zoom",
        "ComboDurationMod" to "combo_duration",
        "SlideAttackCritChanceMod" to "critical_chance_on_slide_attack",
        "WeaponMeleeFinisherDamageMod" to "finisher_damage",
        "WeaponMeleeComboEfficiencyMod" to "channeling_efficiency",
        "WeaponMeleeComboInitialBonusMod" to "channeling_damage",
        "WeaponMeleeComboPointsOnHitMod" to "chance_to_gain_combo_count",
        "WeaponMeleeComboBonusOnHitMod" to "chance_to_gain_extra_combo_count",
        "WeaponAmmoEfficiency" to "ammo_efficiency",
        "WeaponStatusDamage" to "status_damage",
        "WeaponWeakpointDamage" to "weak_point_damage",
        "WeaponWeakpointCriticalChance" to "weak_point_critical_chance",
        "WeaponMagazineReloadHolstered" to "magazine_reloaded_s_when_holstered",
        "WeaponMeleeHeavyAttackDamageMod" to "melee_damage_on_heavy_attack",
        "WeaponMeleeHeavyAttackChargeMod" to "heavy_attack_wind_up_speed",
        "WeaponMeleeParryAngleMod" to "parry_angle",
        "WeaponMeleeSlamDamageMod" to "slam_attack_damage",
        "WeaponCorrosiveDamageMod" to "corrosive",
        "WeaponViralDamageMod" to "viral",
        "WeaponGasDamageMod" to "gas",
        "WeaponBlastDamageMod" to "blast",
        "WeaponRadiationDamageMod" to "radiation",
        "WeaponMagneticDamageMod" to "magnetic",
        "WeaponFactionDamageCorpus" to "damage_vs_corpus",
        "WeaponMeleeFactionDamageCorpus" to "damage_vs_corpus",
        "WeaponFactionDamageGrineer" to "damage_vs_grineer",
        "WeaponMeleeFactionDamageGrineer" to "damage_vs_grineer",
        "WeaponFactionDamageInfested" to "damage_vs_infested",
        "WeaponMeleeFactionDamageInfested" to "damage_vs_infested",
        "WeaponFactionDamageOrokin" to "damage_to_orokin",
        "WeaponFactionDamageScaldra" to "damage_to_scaldra",
        "WeaponFactionDamageTechrot" to "damage_to_techrot",
    )
    private val factionNamesBySlug = mapOf(
        "damage_vs_corpus" to setOf("c", "corpus", "科普斯"),
        "damage_vs_grineer" to setOf("g", "grineer", "克隆尼"),
        "damage_vs_infested" to setOf("i", "infested", "感染者", "感染"),
        "damage_to_orokin" to setOf("o", "orokin", "奥罗金", "orkin"),
        "damage_to_scaldra" to setOf("scaldra", "炽蛇军"),
        "damage_to_techrot" to setOf("techrot", "科腐者"),
    )
    private val namesBySlug = mapOf(
        "critical_chance" to setOf("暴击", "爆击", "暴击率", "爆击率", "暴击几率", "爆击几率", "暴击机率"),
        "critical_damage" to setOf("暴伤", "爆伤", "暴击伤害", "爆击伤害"),
        "base_damage_/_melee_damage" to setOf("伤害", "基础伤害", "基伤", "近战伤害"),
        "fire_rate_/_attack_speed" to setOf("射速", "攻速", "攻击速度", "射速/攻击速度"),
        "multishot" to setOf("多重", "多重射击", "Multishot"),
        "toxin_damage" to setOf("毒伤", "毒素", "毒素伤害"),
        "cold_damage" to setOf("冰伤", "冰冻", "冰冻伤害"),
        "heat_damage" to setOf("火伤", "火焰", "火焰伤害"),
        "electric_damage" to setOf("电伤", "电击", "电击伤害"),
        "impact_damage" to setOf("冲击", "冲击伤害"),
        "puncture_damage" to setOf("穿刺", "穿刺伤害"),
        "slash_damage" to setOf("切割", "切割伤害"),
        "status_chance" to setOf("触发", "触发率", "触发几率", "异常状态几率"),
        "status_duration" to setOf("触发时间", "触发持续时间", "异常持续时间", "异常状态持续时间"),
        "projectile_speed" to setOf("弹速", "投射物速度", "投射物飞行速度", "弹道飞行速度"),
        "reload_speed" to setOf("装填", "装填速度", "换弹", "换弹速度"),
        "magazine_capacity" to setOf("弹匣", "弹匣容量", "弹夹"),
        "ammo_maximum" to setOf("弹药", "弹药上限", "弹药最大值", "最大弹药量"),
        "punch_through" to setOf("穿透"),
        "range" to setOf("范围", "攻击范围", "Range"),
        "recoil" to setOf("后坐", "后坐力", "武器后坐力"),
        "zoom" to setOf("变焦", "变焦倍率"),
        "combo_duration" to setOf("连击持续时间", "连击时间", "连击持续时间"),
        "critical_chance_on_slide_attack" to setOf("滑行攻击暴击几率", "滑暴", "滑行攻击"),
        "finisher_damage" to setOf("处决伤害","处决"),
        "channeling_efficiency" to setOf("重击效率"),
        "channeling_damage" to setOf("初始连击"),
        "chance_to_gain_combo_count" to setOf("几率不获得连击数", "几率无法获得连击", "的几率来获得连击数"),
        "chance_to_gain_extra_combo_count" to setOf("额外连击数几率", "额外连击", "几率获得连击数"),
        "ammo_efficiency" to setOf("弹药效率", "弹效"),
        "status_damage" to setOf("异常伤害", "异常状态伤害", "异常"),
        "weak_point_damage" to setOf("弱点伤害"),
        "weak_point_critical_chance" to setOf("弱点暴击几率", "弱点暴击率"),
        "magazine_reloaded_s_when_holstered" to setOf("收起武器时弹匣每秒自动装填", "收起武器时自动装填", "收起时装填"),
        "melee_damage_on_heavy_attack" to setOf("在重击时的近战伤害", "重击伤害"),
        "heavy_attack_wind_up_speed" to setOf("重击准备速度", "重击蓄力速度"),
        "parry_angle" to setOf("招架角度", "格挡角度"),
        "slam_attack_damage" to setOf("震地攻击伤害", "震地伤害", "震地"),
        "corrosive" to setOf("腐蚀伤害", "腐蚀"),
        "viral" to setOf("病毒伤害", "病毒"),
        "gas" to setOf("毒气伤害", "毒气"),
        "blast" to setOf("爆炸伤害", "爆炸"),
        "radiation" to setOf("辐射伤害", "辐射"),
        "magnetic" to setOf("磁力伤害", "磁力"),
    ) + factionNamesBySlug.mapValues { (_, names) ->
        names.flatMap { name ->
            listOf("对${name}伤害", "对${name}的伤害") + if (name.length > 1 && name.all { it in 'a'..'z' }) {
                listOf("Damage to $name", "Damage vs $name")
            } else emptyList()
        }.toSet()
    }
    private val slugsByName = namesBySlug.flatMap { (slug, names) ->
        names.map { normalize(it) to slug }
    }.toMap()
    private val factionSlugsByName = factionNamesBySlug.flatMap { (slug, names) ->
        names.map { normalize(it) to slug }
    }.toMap()
    private val factionPattern = Regex("对(.+?)佬?(?:的?伤害)?")

    /** 输入和词库共用同一格式，保留词条本身的字词和标点。 */
    fun normalize(name: String): String = Normalizer.normalize(name, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT).filterNot(Char::isWhitespace)

    /** 普通别名完整匹配；阵营只接受已知名称和固定句式。 */
    fun slugFor(name: String): String? {
        val normalized = normalize(name)
        return slugsByName[normalized] ?: factionPattern.matchEntire(normalized)?.let {
            factionSlugsByName[it.groupValues[1]]
        }
    }

    /** 返回同一属性的完整别名，供图片或文字输入与当前武器词条池求交集。 */
    fun namesFor(name: String): Set<String> = slugFor(name)?.let(namesBySlug::getValue).orEmpty()

    /** 按官方 game_ref 取得 WM 标识，供市场参考独立于报告显示名匹配；未知标识返回 null。 */
    fun slugForGameRef(gameRef: String): String? = slugsByGameRef[gameRef]

    /** 按官方 game_ref 取得同组人工别名；未知标识返回空集合，不创建新的数值词条。 */
    fun namesForGameRef(gameRef: String): Set<String> = slugForGameRef(gameRef)?.let(namesBySlug::get).orEmpty()
}