package bot.wuliang.moudles

/**
 * 节点信息
 */
data class Nodes(
    /**
     * 节点名称
     */
    val name: String? = null,
    /**
     * 节点所属阵营
     */
    val faction: String? = null,
    /**
     * 节点任务类型
     */
    val type: String? = null
)
