package bot.wuliang.moudles

import java.time.Instant

/**
 * 灵化
 */
data class Incarnon(
    val thisWeek: IncarnonData? = null,
    val nextWeek: IncarnonData? = null,
    val activation: Instant? = null,
    val expiry: Instant? = null,
    var eta: String? = null
) {
    data class IncarnonData(
        val ordinary: WeekData? = null,
        val steel: WeekData? = null
    )

    data class WeekData(
        val week: Int? = null,
        val items: List<Any>? = listOf(),
    )

    data class SteelItem(
        val name: String? = null,
        val riven: Double? = null,
        val urlName: String? = null,
        val rivenPrice: Double? = null,
    )
}