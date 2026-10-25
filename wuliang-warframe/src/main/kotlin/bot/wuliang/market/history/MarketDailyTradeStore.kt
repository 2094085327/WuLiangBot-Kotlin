package bot.wuliang.market.history

import bot.wuliang.entity.WfMarketItemEntity
import bot.wuliang.mapper.MarketDailyTradeMapper
import bot.wuliang.mapper.WfMarketItemMapper
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDate

@Service
class MarketDailyTradeStore(
    private val mapper: MarketDailyTradeMapper,
    private val items: WfMarketItemMapper,
) {
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = [Exception::class], timeout = 30)
    fun saveCatalogBatch(catalog: List<WfMarketItemEntity>, ensureLease: () -> Unit) {
        ensureLease()
        items.upsertCatalogMetadata(catalog)
        ensureLease()
    }

    /** 每 500 条独立提交；重试按主键覆盖。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = [Exception::class], timeout = 30)
    fun saveTradeBatch(records: List<MarketDailyTradeAverage>, ensureLease: () -> Unit) {
        ensureLease()
        mapper.insertRecords(records)
        ensureLease()
    }

    /** 读到文件末尾后，事务内清理旧重试残留并核对数量；返回后才更新 Redis 发布日。 */
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = [Exception::class], timeout = 30)
    fun finalizeImport(
        day: LocalDate,
        imported: MarketDailyTradeImport,
        ensureLease: () -> Unit
    ): MarketTradePublication {
        ensureLease()
        mapper.deleteStaleDay(day, imported.fetchedAt)
        check(mapper.countImportedRecords(day, imported.fetchedAt) == imported.recordCount) {
            "归档入库数量与完整文件不符，保留旧榜"
        }
        val publication =
            MarketTradePublication(day, System.currentTimeMillis(), imported.itemCount, imported.recordCount)
        ensureLease()
        return publication
    }

    /** 只保留最近两期已发布数据，清理其他日的残留。 */
    fun deleteExpiredBatch(day: LocalDate, previousDay: LocalDate): Int =
        mapper.deleteExpired(day, previousDay)

    fun ranking(
        criteria: MarketRankingCriteria,
        day: LocalDate,
        limit: Int = criteria.pageSize + 1,
    ): List<MarketDailyTradeAverage> {
        return mapper.selectRanking(day, criteria, (criteria.page - 1L) * criteria.pageSize, limit)
    }
}