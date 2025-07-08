package bot.wuliang.moudles

import java.time.Instant

/**
 * 钢铁之路
 */
data class SteelPath(
    val id: String? = null,
    /**
     * 开始时间 UTC时间
     */
    val activation: Instant? = null,
    /**
     * 结束时间 UTC时间
     */
    val expiry: Instant? = null,
    /**
     * 剩余时间
     */
    var eta: String? = null,
    /**
     * 当前物品
     */
    val currentItem: String? = null,
    /**
     * 当前物品价格
     */
    val currentCost: Int? = null,
    /**
     * 下周物品
     */
    val nextItem: String? = null,
    /**
     * 下周物品价格
     */
    val nextCost: Int? = null,
)
