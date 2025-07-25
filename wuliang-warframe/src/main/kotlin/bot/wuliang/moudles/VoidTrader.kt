package bot.wuliang.moudles

import java.time.Instant

/**
 * 虚空商人
 */
data class VoidTrader(
    val id: String? = null,
    val activation: Instant? = null,
    val expiry: Instant? = null,
    val eta: String? = null,
    val isActive: Boolean? = null,
    val node: String? = null,
    val inventory: List<VoidTraderItem>? = null
)
