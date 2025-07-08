package bot.wuliang.moudles

import java.time.Instant

data class EarthCycle(
    val expiry: Instant,
    val activation: Instant,
    val isDay: Boolean,
    val state: String,
    val timeLeft: String,
    val id: String
)