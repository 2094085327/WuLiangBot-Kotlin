package bot.wuliang.entity.vo

import bot.wuliang.utils.WfUtil


/**
 * @description: Warframe Status Vo层
 * @author Nature Zero
 * @date 2024/8/21 上午9:38
 */
class WfStatusVo {

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