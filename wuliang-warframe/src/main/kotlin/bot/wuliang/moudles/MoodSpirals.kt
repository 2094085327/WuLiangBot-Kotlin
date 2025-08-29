package bot.wuliang.moudles

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
data class MoodSpirals(
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
