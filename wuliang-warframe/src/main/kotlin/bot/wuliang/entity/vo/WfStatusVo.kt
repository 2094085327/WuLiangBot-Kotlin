package bot.wuliang.entity.vo

import bot.wuliang.utils.WfUtil


/**
 * @description: Warframe Status Vo层
 * @author Nature Zero
 * @date 2024/8/21 上午9:38
 */
class WfStatusVo {

    /**
     * 入侵信息
     *
     * @property itemString 物品名称
     * @property factions 阵营
     */
    data class Invasions(
        val itemString: String? = null,
        val factions: String? = null,
    )

    /**
     * 入侵信息 Entity
     *
     * @property node 入侵节点
     * @property invasionsDetail 入侵详情
     * @property completion 入侵完成度
     */
    data class InvasionsEntity(
        val node: String? = null,
        val invasionsDetail: List<Invasions>? = null,
        val completion: Double? = null
    )

    /**
     * 灵化信息
     *
     * @property thisWeekData 本周数据
     * @property nextWeekData 下周数据
     * @property remainTime 剩余时间
     */
    data class IncarnonEntity(
        val thisWeekData: WfUtil.Data? = null,
        val nextWeekData: WfUtil.Data? = null,
        var remainTime: String? = null
    )

    /**
     * 双衍平原状态信息
     *
     * @property currentState 当前状态
     * @property damageType 伤害类型
     * @property npc 当前出现的NPC列表
     * @property excludeNpc 当前不出现的NPC列表
     * @property excludePlace 当前不出现的地点列表
     * @property noExcludePlace 当前出现的地点列表
     * @property remainTime 距离下个状态的剩余时间
     * @property nextState 下个状态
     * @property nextExcludePlace 下个状态不出现的地点列表
     */
    data class MoodSpiralsEntity(
        val currentState: String? = null,
        val damageType: String? = null,
        val npc: List<Map<String, String>>? = null,
        val excludeNpc: List<Map<String, String>>? = null,
        val excludePlace: List<String>? = null,
        val noExcludePlace: List<String>? = null,
        var remainTime: String? = null,
        val nextState: String? = null,
        val nextExcludePlace: List<String>? = null,
    )

    /**
     * 世界状态信息
     *
     * @property displayState 状态
     * @property activation 开始时间
     * @property expiry 结束时间
     * @property timeLeft 剩余时间
     */
    data class WordStatus(
        val displayState: String? = null,
        val activation: String? = null,
        val expiry: String? = null,
        val timeLeft: String? = null
    )
}