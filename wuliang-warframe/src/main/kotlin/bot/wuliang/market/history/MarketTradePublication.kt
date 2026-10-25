package bot.wuliang.market.history

import java.time.LocalDate

/** 完整归档全部分批入库后写入 Redis 的当前发布元数据。 */
data class MarketTradePublication(
    var tradeDay: LocalDate = LocalDate.ofEpochDay(0),
    var publishedAt: Long = 0,
    var itemCount: Long = 0,
    var recordCount: Long = 0,
)