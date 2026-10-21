package bot.wuliang.market.history

import java.time.LocalDate

/** 按商品计数，成功无成交也算处理成功；失败和未尝试商品始终留在分母。 */
data class MarketCollectionProgress(
    var totalItems: Long = 0,
    var processedItems: Long = 0,
    var retryableItems: Long = 0,
    var exhaustedItems: Long = 0,
) {
    val publishable: Boolean get() = totalItems > 0 && processedItems * 100 >= totalItems * 99
}

/** 同步表中的逐商品已发布批次事实，用于重建丢失的 Redis 发布标记。 */
data class MarketPublicationCheckpoint(
    var tradeDay: LocalDate = LocalDate.ofEpochDay(0),
    var totalItems: Long = 0,
    var processedItems: Long = 0,
)