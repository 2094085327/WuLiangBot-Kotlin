package bot.wuliang.market.history

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.LocalDate

data class MarketRankingEntry(
    val position: Long,
    val itemId: String,
    val name: String,
    val urlName: String?,
    val variantKey: String,
    val variantJson: String,
    val rank: Int,
    val variantLabel: String,
    val value: BigDecimal,
    val fetchedAt: Long,
    val tradeAverage: BigDecimal? = null,
    val tradeDay: LocalDate? = null,
    val tradeVolume: Long? = null,
    val tradeMinPrice: BigDecimal? = null,
    val tradeMaxPrice: BigDecimal? = null,
)

data class MarketRankingSection(
    val category: MarketItemCategory,
    val title: String,
    val rankSelection: MarketRankSelection,
    val exactRank: Int?,
    val page: Int,
    val pageSize: Int,
    val hasNext: Boolean,
    val items: List<MarketRankingEntry>,
    val fullCategory: Boolean = false,
    val displayLimit: Int? = null,
    val truncated: Boolean = false,
)

data class MarketRankingResult(
    val metric: MarketRankingMetric,
    val metricLabel: String,
    val descending: Boolean,
    val overview: Boolean,
    val generatedAt: Long,
    val sections: List<MarketRankingSection>,
    val tradeDay: LocalDate? = null,
    val publication: MarketTradePublication? = null,
)

/** HTTP 与机器人共用已发布的本地完整日榜单。 */
@Service
class MarketRankingService(
    private val trades: MarketDailyTradeStore,
    private val publications: MarketTradePublicationService,
    private val objectMapper: ObjectMapper,
) {
    fun query(request: MarketRankingRequest): MarketRankingResult {
        val categories = request.category?.let(::listOf) ?: OVERVIEW_CATEGORIES
        val publication = publications.current()
        val tradeDay = publication?.tradeDay
        val sections = categories.map { category ->
            val criteria = request.criteria(category, if (request.category == null) 3 else request.pageSize)
            val displayLimit = if (request.fullCategory && category == MarketItemCategory.MOD) 30 else null
            val records = if (tradeDay == null) emptyList() else trades.ranking(
                criteria, tradeDay,
                if (request.fullCategory) displayLimit?.plus(1) ?: Int.MAX_VALUE else criteria.pageSize + 1
            )
            val size = if (request.fullCategory) displayLimit ?: records.size else criteria.pageSize
            val entries = records.take(size).mapIndexed { index, row ->
                tradeEntry(row, (criteria.page - 1L) * size + index + 1, request.metric)
            }
            MarketRankingSection(
                category, categoryLabel(category), criteria.rankSelection, criteria.exactRank,
                criteria.page, size, !request.fullCategory && records.size > size, entries,
                request.fullCategory, displayLimit,
                request.fullCategory && displayLimit != null && records.size > displayLimit,
            )
        }
        return MarketRankingResult(
            metric = request.metric,
            metricLabel = metricLabel(request.metric),
            descending = request.descending,
            overview = request.category == null,
            generatedAt = System.currentTimeMillis(),
            sections = sections,
            tradeDay = tradeDay,
            publication = publication,
        )
    }

    private fun tradeEntry(row: MarketDailyTradeAverage, position: Long, metric: MarketRankingMetric) =
        MarketRankingEntry(
            position = position,
            itemId = row.itemId,
            name = row.zhName?.takeIf(String::isNotBlank) ?: row.enName?.takeIf(String::isNotBlank) ?: row.urlName
            ?: row.itemId,
            urlName = row.urlName,
            variantKey = row.variantKey,
            variantJson = row.variantJson,
            rank = row.rank,
            variantLabel = variantLabel(row.rank, row.maxRank, row.variantJson),
            value = when (metric) {
                MarketRankingMetric.TRADE_AVERAGE -> row.averagePrice
                MarketRankingMetric.TRADE_MIN_PRICE -> requireNotNull(row.minPrice)
                MarketRankingMetric.TRADE_MAX_PRICE -> requireNotNull(row.maxPrice)
                MarketRankingMetric.TRADE_VOLUME -> row.volume.toBigDecimal()
            },
            fetchedAt = row.fetchedAt,
            tradeAverage = row.averagePrice,
            tradeDay = row.tradeDay,
            tradeVolume = row.volume,
            tradeMinPrice = row.minPrice,
            tradeMaxPrice = row.maxPrice,
        )

    private fun variantLabel(rank: Int, maxRank: Int?, variantJson: String): String = buildList {
        if (rank >= 0) add(if (maxRank == rank && rank > 0) "满级/${rank}级" else "${rank}级")
        // 非等级变体也明确显示，展开全部等级时不会把不同规格伪装成重复商品。
        val node = objectMapper.readTree(variantJson)
        node["subtype"]?.asText()?.let { add(it) }
        node["amberStars"]?.asInt()?.let { add("琥珀星$it") }
        node["cyanStars"]?.asInt()?.let { add("青蓝星$it") }
        node["charges"]?.asInt()?.let { add("充能$it") }
    }.joinToString("，")

    companion object {
        val OVERVIEW_CATEGORIES = listOf(
            MarketItemCategory.WARFRAME,
            MarketItemCategory.WEAPON,
            MarketItemCategory.PRIME_WEAPON,
            MarketItemCategory.ARCANE,
            MarketItemCategory.PRIME_MOD,
            MarketItemCategory.MOD
        )

        fun categoryLabel(category: MarketItemCategory): String = when (category) {
            MarketItemCategory.ALL -> "全部物品"
            MarketItemCategory.WARFRAME -> "战甲套装"
            MarketItemCategory.WEAPON -> "普通武器"
            MarketItemCategory.PRIME_WEAPON -> "Prime 武器套装"
            MarketItemCategory.ARCANE -> "赋能"
            MarketItemCategory.PRIME_MOD -> "Prime MOD"
            MarketItemCategory.MOD -> "普通 MOD"
        }

        fun metricLabel(metric: MarketRankingMetric): String = when (metric) {
            MarketRankingMetric.TRADE_AVERAGE -> "每日成交加权均价"
            MarketRankingMetric.TRADE_MIN_PRICE -> "每日最低成交价"
            MarketRankingMetric.TRADE_MAX_PRICE -> "每日最高成交价"
            MarketRankingMetric.TRADE_VOLUME -> "每日成交量"
        }

    }
}