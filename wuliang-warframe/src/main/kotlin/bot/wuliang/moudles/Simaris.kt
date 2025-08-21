package bot.wuliang.moudles

import java.time.Instant

/**
 * 圣殿结合仪式目标
 */
data class Simaris(
    /**
     * 图片key
     */
    val imageKey: String? = null,
    /**
     * 目标名称
     */
    val name: String? = null,
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
     * 目标出现位置
     */
    val locations:List<SimarisLocation>? = null,
)
