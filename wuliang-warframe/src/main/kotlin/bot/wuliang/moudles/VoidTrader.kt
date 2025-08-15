package bot.wuliang.moudles

import com.fasterxml.jackson.annotation.JsonFormat
import java.time.Instant

/**
 * 虚空商人
 */
data class VoidTrader(
    val id: String? = null,
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    val activation: Instant? = null,
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    val expiry: Instant? = null,
    val eta: String? = null,
    val isActive: Boolean? = null,
    val node: String? = null,
    val inventory: List<VoidTraderItem>? = null
)
