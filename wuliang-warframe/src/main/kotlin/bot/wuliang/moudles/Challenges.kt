package bot.wuliang.moudles

import com.alibaba.fastjson2.annotation.JSONField

/**
 * 电波任务
 */
data class Challenges(
    var id: String? = null,

    @JSONField(name = "isDaily")
    var isDaily: Boolean? = null,
    @JSONField(name = "isElite")
    var isElite: Boolean? = null,
    @JSONField(name = "isPermanent")
    var isPermanent: Boolean? = null,
    var title: String? = null,
    var desc: String? = null,

    /**
     * 奖励声望
     */
    var reputation: Int? = null,
)
