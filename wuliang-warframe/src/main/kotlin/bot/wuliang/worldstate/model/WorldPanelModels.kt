package bot.wuliang.worldstate.model

/** 面板专用响应；时间统一使用 Unix 毫秒，玩法协议模型由解析器独立维护。 */
data class WorldEntry(
    val id: String,
    val title: String,
    val category: String = "",
    val subtitle: String = "",
    val details: List<String> = emptyList(),
    val rewards: List<String> = emptyList(),
    val activation: Long? = null,
    val expiry: Long? = null,
    val progress: Double? = null,
)

data class WorldSection(val id: String, val title: String, val entries: List<WorldEntry>, val note: String? = null)
data class WorldPanel(
    val fetchedAt: Long,
    val worldTime: Long,
    val serverTime: Long,
    val stale: Boolean,
    val refreshAfterSeconds: Int = 300,
    val cycles: List<WorldEntry>,
    val sections: List<WorldSection>,
)