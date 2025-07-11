package bot.wuliang.moudles

import java.time.Instant

/**
 * 虚空裂缝
 */
data class Fissure(
    val id: String? = null,
    val activation: Instant? = null,
    val expiry: Instant? = null,
    var eta: String? = null,
    val node: String? = null,
    val missionType: String? = null,
    val modifier: String? = null,
    val modifierValue: Int? = null,
    val faction: String? = null,
    val hard: Boolean? = null,
    val storm: Boolean? = null,
)
