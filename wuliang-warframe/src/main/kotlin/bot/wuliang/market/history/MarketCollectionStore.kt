package bot.wuliang.market.history

import bot.wuliang.entity.WfMarketItemEntity
import bot.wuliang.mapper.MarketCollectionMapper
import bot.wuliang.mapper.WfMarketItemMapper
import bot.wuliang.market.MarketProperties
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

/** 目录元数据与逐商品进度。 */
@Service
class MarketCollectionStore(
    private val mapper: MarketCollectionMapper,
    private val items: WfMarketItemMapper,
    private val properties: MarketProperties,
) {
    fun syncCatalog(catalog: List<WfMarketItemEntity>) = catalog.chunked(500).forEach(items::upsertCatalogMetadata)

    @Transactional
    fun prepareDay(itemIds: List<String>, day: LocalDate) {
        // 每日首次准备时冻结商品范围；重试不缩小失败分母，也不临时纳入新目录条目。
        if (mapper.selectProgress(
                properties.statistics.platform, MarketDailyTradeAverage.SOURCE,
                day, properties.statistics.dailyRetryLimit + 1
            ).totalItems > 0
        ) return
        itemIds.chunked(500).forEach {
            mapper.prepareDay(it, properties.statistics.platform, MarketDailyTradeAverage.SOURCE, day)
        }
    }

    fun dueDailyItemIds(platform: String, source: String, day: LocalDate): List<String> =
        mapper.findDueDailyItemIds(platform, source, day, properties.statistics.dailyRetryLimit + 1)

    fun progress(day: LocalDate): MarketCollectionProgress = mapper.selectProgress(
        properties.statistics.platform, MarketDailyTradeAverage.SOURCE, day, properties.statistics.dailyRetryLimit + 1,
    )

    fun markAttempt(itemId: String, source: String, day: LocalDate): Boolean =
        mapper.markAttempt(
            itemId, properties.statistics.platform, source, System.currentTimeMillis(),
            day, properties.statistics.dailyRetryLimit + 1
        ) == 1

    fun markFailure(itemId: String, source: String, error: Exception) = mapper.markFailure(
        itemId, properties.statistics.platform, source, System.currentTimeMillis(),
        (error.message ?: error.javaClass.simpleName).take(1000),
    )
}