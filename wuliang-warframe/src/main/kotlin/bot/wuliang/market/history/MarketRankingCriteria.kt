package bot.wuliang.market.history

enum class MarketRankingMetric { TRADE_AVERAGE, TRADE_MIN_PRICE, TRADE_MAX_PRICE, TRADE_VOLUME }

enum class MarketRankSelection { PREFERRED, ALL, BASE, MAX, EXACT }

/** 每日成交排行榜的分类、等级和分页条件。 */
data class MarketRankingCriteria(
    val category: MarketItemCategory = MarketItemCategory.ALL,
    val rankSelection: MarketRankSelection = MarketRankSelection.PREFERRED,
    val exactRank: Int? = null,
    val descending: Boolean = true,
    val page: Int = 1,
    val pageSize: Int = 20,
    val metric: MarketRankingMetric = MarketRankingMetric.TRADE_AVERAGE,
) {
    init {
        require(page >= 1 && pageSize in 1..100)
        require(rankSelection != MarketRankSelection.EXACT || (exactRank != null && exactRank >= 0))
    }
}