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
    init {
        require(page in 1..1000 && pageSize in 1..100) { "页码需在 1–1000 之间，每页需在 1–100 之间。" }
        require(exactRank == null || exactRank in 0..100) { "等级需在 0–100 之间。" }
        require(rankSelection != MarketRankSelection.EXACT || exactRank != null) { "EXACT 模式必须提供 exactRank。" }
        require(exactRank == null || rankSelection == MarketRankSelection.EXACT) { "exactRank 只能与 EXACT 模式一起使用。" }
        require(category != null || page == 1) { "综合榜不分页，请指定分类或 ALL。" }
        require(!fullCategory || category != null && category != MarketItemCategory.ALL && page == 1) {
            "分类全榜需指定具体分类，且不能同时指定页码。"
        }
    }

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