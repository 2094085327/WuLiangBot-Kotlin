package bot.wuliang.parser.model.wikia

/**
 * 武器攻击属性数据类
 * @param name 攻击名称
 * @param duration 攻击持续时间
 * @param radius 攻击半径
 * @param speed 攻击速度
 * @param pellet 弹丸统计信息
 * @param critChance 暴击几率
 * @param critMult 暴击倍率
 * @param statusChance 触发几率
 * @param chargeTime 蓄力时间
 * @param shotType 射击类型
 * @param shotSpeed 射弹速度
 * @param flight 飞行速度
 * @param falloff 衰减统计信息
 * @param damage 伤害类型及其数值
 * @param slide 滑砍属性
 * @param jump 跳砍属性
 * @param wall 攀附攻击属性
 * @param channeling
 * @param slam 震地攻击属性
 */
data class WikiaAttack(
    val name: String,

    val duration: Double? = null,

    val radius: Double? = null,

    val speed: Double? = null,

    val pellet: Pellet? = null,

    val critChance: Double? = null,

    val critMult: Double? = null,

    val statusChance: Double? = null,

    val chargeTime: Double? = null,

    val shotType: String? = null,

    val shotSpeed: Double? = null,

    val flight: Double? = null,

    val falloff: Falloff? = null,

    val damage: Map<String, Double>? = null,

    val slide: String? = null,

    val jump: String? = null,

    val wall: String? = null,

    val channeling: Double? = null,

    val slam: SlamAttack? = null,
) {
    /**
     * 子弹信息
     * @param name 子弹名称
     * @param count 子弹数量
     */
    data class Pellet(
        val name: String? = null,
        val count: Int? = null
    )

    /**
     * 衰减统计信息
     * @param start 衰减开始距离
     * @param end 衰减结束距离
     * @param reduction 衰减值
     */
    data class Falloff(
        val start: Int? = null,
        val end: Int? = null,
        val reduction: Int? = null
    )

    /**
     * 震地攻击数据类
     * @param damage 震地伤害
     * @param radial 范围攻击统计信息
     */
    data class SlamAttack(
        val damage: Double? = null,
        val radial: SlamRadial? = null
    )

    /**
     * 范围攻击统计信息
     * @param damage 范围伤害
     * @param element 元素类型（可选）
     * @param proc 触发类型（可选）
     * @param radius 范围攻击半径
     */
    data class SlamRadial(
        val damage: Double? = null,
        val element: String? = null,
        val proc: String? = null,
        val radius: Double? = null
    )
}
