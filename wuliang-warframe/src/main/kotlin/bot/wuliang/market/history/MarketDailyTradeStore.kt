package bot.wuliang.market.history

import bot.wuliang.mapper.MarketCollectionMapper
import bot.wuliang.mapper.MarketDailyTradeMapper
import bot.wuliang.market.MarketProperties
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
class MarketDailyTradeStore(
    private val mapper: MarketDailyTradeMapper,
    private val sync: MarketCollectionMapper,
    private val properties: MarketProperties,
) {
    /** 空结果表示当日没有可用成交聚合 */
    @Transactional
    fun save(itemId: String, day: LocalDate, records: List<MarketDailyTradeAverage>) {
        require(records.all { it.itemId == itemId && it.tradeDay == day && it.platform == properties.statistics.platform && it.source == MarketDailyTradeAverage.SOURCE })
        mapper.deleteItemDay(itemId, properties.statistics.platform, MarketDailyTradeAverage.SOURCE, day)
        records.chunked(100).forEach(mapper::insertRecords)
        sync.markDataSuccess(
            itemId,
            properties.statistics.platform,
            MarketDailyTradeAverage.SOURCE,
            System.currentTimeMillis(),
            day
        )
    }

    /** 决定额外保留哪一天。 */
    fun previousDataDay(day: LocalDate): LocalDate? = mapper.selectPreviousDataDay(
        properties.statistics.platform, MarketDailyTradeAverage.SOURCE, day,
    )

    /** 持久化逐商品批次事实，事务提交后调用方才能更新 Redis 发布标记。 */
    @Transactional
    fun checkpointPublication(day: LocalDate): MarketCollectionProgress? {
        val progress = sync.selectProgress(
            properties.statistics.platform, MarketDailyTradeAverage.SOURCE,
            day, properties.statistics.dailyRetryLimit + 1
        )
        if (!progress.publishable || mapper.countDay(
                properties.statistics.platform,
                MarketDailyTradeAverage.SOURCE,
                day
            ) == 0L
        ) {
            return null
        }
        sync.checkpointPublication(properties.statistics.platform, MarketDailyTradeAverage.SOURCE, day)
        return progress
    }

    fun publicationCheckpoint(): MarketPublicationCheckpoint? = sync.selectPublicationCheckpoint(
        properties.statistics.platform, MarketDailyTradeAverage.SOURCE,
    )

    /** 只保留本轮目标日和仍在展示的旧日。 */
    fun deleteExpiredBatch(targetDay: LocalDate, previousDay: LocalDate): Int = mapper.deleteExpired(
        properties.statistics.platform, MarketDailyTradeAverage.SOURCE, targetDay, previousDay, 5000,
    )

    /** 查询固定完整UTC日，所有商品同一统计窗口。 */
    fun ranking(
        criteria: MarketRankingCriteria,
        day: LocalDate,
        limit: Int = criteria.pageSize + 1
    ): List<MarketDailyTradeAverage> {
        require(limit > 0)
        return mapper.selectRanking(
            properties.statistics.platform, MarketDailyTradeAverage.SOURCE, day,
            criteria, (criteria.page - 1L) * criteria.pageSize, limit
        )
    }
}