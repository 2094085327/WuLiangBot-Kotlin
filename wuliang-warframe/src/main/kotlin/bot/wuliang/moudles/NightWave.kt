package bot.wuliang.moudles

import java.time.Instant

/**
 * 午夜电波信息
 */
data class NightWave(
    val id: String? = null,
    val activation: Instant? = null,
    val expiry: Instant? = null,
    var startTime: String? = null,
    var eta: String? = null,
    val tag: String? = null,
    val params: String? = null,
    val season: Int? = null,
    val phase: Int? = null,
    val possibleChallenges: List<Challenges>? = null,
    val activeChallenges: List<Challenges>? = null
)
