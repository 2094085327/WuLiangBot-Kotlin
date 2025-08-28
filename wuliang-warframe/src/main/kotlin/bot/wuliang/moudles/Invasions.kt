package bot.wuliang.moudles

import com.fasterxml.jackson.annotation.JsonFormat
import java.time.Instant

/**
 * 入侵信息
 */
data class Invasions(
    var id: String? = null,

    /**
     * 入侵开始时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    val activation: Instant? = null,

    /**
     * 时间字符串
     */
    val eta: String? = null,

    /**
     * 入侵描述
     */
    var desc: String? = null,

    /**
     * 入侵方阵营
     */
    var faction: String? = null,

    /**
     * 防御方阵营
     */
    var defenderFaction: String? = null,

    /**
     * 发生入侵的节点
     */
    var node: String? = null,

    /**
     * 已完成任务的计数。支持进攻方会让计数上升，支持防守方会让计数下降
     */
    var count: Int? = null,

    /**
     * 一方获胜所需的计数数量
     */
    var requiredRuns: Int? = null,

    /**
     * 入侵的完成百分比。如果达到 0，防守方获胜 Grineer vs. Corpus 入侵从 50 开始，Infested 入侵从 100 开始
     */
    var completion: Double? = null,

    /**
     * 入侵是否结束
     */
    var completed: Boolean? = null,

    /**
     * 是否是 Infested 入侵
     */
    var vsInfestation: Boolean? = null,

    /**
     * 进攻方奖励列表
     */
    var attackerReward: List<Modifiers>? = null,

    /**
     * 防守方奖励列表
     */
    var defenderReward: List<Modifiers>? = null,
)
