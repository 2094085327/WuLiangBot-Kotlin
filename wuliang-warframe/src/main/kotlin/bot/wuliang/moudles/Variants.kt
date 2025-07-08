package bot.wuliang.moudles

/**
 * 突击任务信息
 */
data class Variants(
    /**
     * 突击任务类型
     */
    val missionType: String? = null,
    /**
     * 突击任务强化类型
     */
    val modifierType: String? = null,
    /**
     * 突击任务节点
     */
    val node: String? = null,
)
