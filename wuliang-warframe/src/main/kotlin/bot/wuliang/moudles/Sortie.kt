package bot.wuliang.moudles

import java.time.Instant

/**
 * 突击信息
 */
data class Sortie(
    /**
     * ID
     */
    val id: String? = null,

    /**
     * 开始时间，ISO-8601格式时间戳
     */
    val activation: Instant? = null,

    /**
     * 结束时间，ISO-8601格式时间戳
     */
    val expiry: Instant? = null,

    /**
     * 奖励
     */
    val rewardItem: String? = null,

    /**
     * 下周奖励
     */
    val nextRewardItem: String? = null,


    /**
     * 突击boss
     */
    val boss: String? = null,

    /**
     * 下周boss
     */
    val nextBoss: String? = null,

    /**
     * 剩余时间
     */
    var eta: String? = null,

    /**
     * 派系
     */
    val faction: String? = null,

    /**
     * 突击任务
     */
    val variants: List<Variants>? = listOf()
)