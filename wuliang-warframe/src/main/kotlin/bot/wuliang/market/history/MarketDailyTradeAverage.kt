package bot.wuliang.market.history

import java.math.BigDecimal
import java.time.LocalDate

/** WM已关闭订单的单日聚合。 */
data class MarketDailyTradeAverage(
    var itemId: String = "",
    var platform: String = "pc",
    var source: String = SOURCE,
    var tradeDay: LocalDate = LocalDate.ofEpochDay(0),
    var variantKey: String = "",
    var variantJson: String = "{}",
    var rank: Int = -1,
    var minPrice: BigDecimal? = null,
    var maxPrice: BigDecimal? = null,
    var averagePrice: BigDecimal = BigDecimal.ZERO,
    var volume: Long = 0,
    var fetchedAt: Long = 0,
    var urlName: String? = null,
    var zhName: String? = null,
    var enName: String? = null,
    var maxRank: Int? = null,
) {
    companion object {
        const val SOURCE = "wm_v1_closed_daily"
    }
}