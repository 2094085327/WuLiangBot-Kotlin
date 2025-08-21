package bot.wuliang.moudles

/**
 * 结合仪式目标缓存数据
 */
data class SimarisPersistent(
    /**
     * 图片key
     */
    val imageKey: String? = null,
    /**
     * 目标名称
     */
    val name: String? = null,

    /**
     * 目标出现位置
     */
    val locations: List<SimarisLocation>? = null,
)
