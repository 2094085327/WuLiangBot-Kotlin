package bot.wuliang.moudles

import java.time.Instant

data class News(
    val id: String? = null,
    val message: String? = null,
    val link: String? = null,
    val imageLink: String? = null,
    val priority: Boolean? = null,
    val date: Instant? = null,
    val startDate: Instant? = null,
    val endDate: Instant? = null
)