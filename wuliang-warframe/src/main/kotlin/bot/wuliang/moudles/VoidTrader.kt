package bot.wuliang.moudles

import com.fasterxml.jackson.annotation.JsonAlias
import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.annotation.JsonProperty
import java.time.Instant

/**
 * 虚空商人
 */
data class VoidTrader(
    @JsonProperty("id")
    val id: String? = null,
    @JsonProperty("activation")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    val activation: Instant? = null,
    @JsonProperty("expiry")
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    val expiry: Instant? = null,
    @JsonProperty("eta")
    var eta: String? = null,
    @JsonProperty("isActive")
    @JsonAlias("active")
    val isActive: Boolean? = null,
    @JsonProperty("node")
    val node: String? = null,
    @JsonProperty("inventory")
    val inventory: List<VoidTraderItem>? = null
)