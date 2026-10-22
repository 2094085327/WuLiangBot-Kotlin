package bot.wuliang.market.history

/** 分类为空时展示六类综合榜。 */
data class MarketRankingRequest(
    val category: MarketItemCategory? = null,
    val metric: MarketRankingMetric = MarketRankingMetric.TRADE_AVERAGE,
    val rankSelection: MarketRankSelection? = null,
    val exactRank: Int? = null,
    val descending: Boolean = true,
    val page: Int = 1,
    val pageSize: Int = 20,
    val fullCategory: Boolean = false,
) {
    fun criteria(forCategory: MarketItemCategory, size: Int = pageSize): MarketRankingCriteria {
        val requested = rankSelection ?: defaultRank(forCategory, overview = category == null)
        // 套装和普通成品武器无可升级等级，允许复用 MAX 等级筛选。
        val effective = if (requested == MarketRankSelection.MAX && forCategory in UNRANKED_CATEGORIES)
            MarketRankSelection.PREFERRED else requested
        return MarketRankingCriteria(
            category = forCategory, rankSelection = effective,
            exactRank = exactRank, descending = descending, page = page, pageSize = size, metric = metric
        )
    }

    companion object {
        private val UNRANKED_CATEGORIES =
            setOf(MarketItemCategory.WARFRAME, MarketItemCategory.WEAPON, MarketItemCategory.PRIME_WEAPON)

        fun defaultRank(category: MarketItemCategory, overview: Boolean = false): MarketRankSelection =
            when (category) {
                MarketItemCategory.MOD, MarketItemCategory.PRIME_MOD, MarketItemCategory.ARCANE ->
                    if (overview) MarketRankSelection.MAX else MarketRankSelection.BASE

                else -> MarketRankSelection.PREFERRED
            }
    }
}

/** 统一使用枚举名。 */
object MarketRankingQuery {
    fun category(value: String): MarketItemCategory =
        MarketItemCategory.entries.firstOrNull { it.name.equals(value, true) }
            ?: throw IllegalArgumentException("未知分类：$value")

    fun metric(value: String): MarketRankingMetric =
        MarketRankingMetric.entries.firstOrNull { it.name.equals(value, true) }
            ?: throw IllegalArgumentException("未知指标：$value；支持 ${MarketRankingMetric.entries.joinToString { it.name }}。")

    fun rankSelection(value: String): MarketRankSelection =
        MarketRankSelection.entries.firstOrNull { it.name.equals(value, true) }
            ?: throw IllegalArgumentException("未知等级模式：$value")
}