package bot.wuliang.moudles

/**
 * 圣殿结合仪式目标出现位置
 */
data class SimarisLocation(
    /**
     * 任务等级
     * 表示该位置的任务难度等级
     */
    val level: String? = null,

    /**
     * 敌人阵营
     * 表示在该位置出现的敌人所属的阵营
     */
    val faction: String? = null,

    /**
     * 刷新率
     * 表示目标在该位置的出现概率或刷新频率
     */
    val spawnRate: String? = null,

    /**
     * 任务类型
     * 表示该位置的任务类型（如：歼灭、生存等）
     */
    val mission: String? = null,

    /**
     * 星球
     * 表示目标出现的星球位置
     */
    val planet: String? = null,

    /**
     * 节点类型
     * 表示具体的任务节点类型
     */
    val type: String? = null,
)