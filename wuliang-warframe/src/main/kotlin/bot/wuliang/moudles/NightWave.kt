package bot.wuliang.moudles

import com.fasterxml.jackson.annotation.JsonFormat
import java.time.Instant

/**
 * 午夜电波信息
 */
data class NightWave(
    val id: String? = null,
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    val activation: Instant? = null,
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
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
