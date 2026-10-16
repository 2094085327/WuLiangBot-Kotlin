package bot.wuliang.worldstate.model

/** 赏金的固定顺序、官方集团标识与对外显示命令。 */
enum class BountyGroup(val id: String, val tag: String, val command: String, val challenge: Boolean = false) {
    EARTH("earth", "CetusSyndicate", "地球赏金"),
    VENUS("venus", "SolarisSyndicate", "金星赏金"),
    DEIMOS("deimos", "EntratiSyndicate", "火卫二赏金"),
    ZARIMAN("zariman", "ZarimanSyndicate", "扎里曼赏金", true),
    SANCTUM("sanctum", "EntratiLabSyndicate", "圣所赏金", true),
    HEX("hex", "HexSyndicate", "1999赏金", true),
}

data class BountyBoard(
    val id: String,
    val title: String,
    val section: WorldSection,
    val activation: Long?,
    val expiry: Long?,
)

data class BountyReport(
    val groups: List<BountyBoard>,
    val fetchedAt: Long,
    val serverTime: Long,
    val stale: Boolean,
)