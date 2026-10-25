package bot.wuliang.scheduling

import bot.wuliang.botLog.logUtil.LoggerUtils.logError
import bot.wuliang.botLog.logUtil.LoggerUtils.logInfo
import bot.wuliang.market.MarketCatalogSource
import bot.wuliang.market.MarketProperties
import bot.wuliang.market.history.MarketCollectionLease
import bot.wuliang.market.history.MarketDailyTradeSource
import bot.wuliang.market.history.MarketDailyTradeStore
import bot.wuliang.market.history.MarketTradePublicationService
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.annotation.PreDestroy

/** 流式读取完整 UTC 日归档，每 500 条提交；全部写入后切换榜单。 */
@Component
class MarketStatisticsCollector(
    private val properties: MarketProperties,
    private val catalog: MarketCatalogSource,
    private val tradeSource: MarketDailyTradeSource,
    private val tradeStore: MarketDailyTradeStore,
    private val publications: MarketTradePublicationService,
    private val lease: MarketCollectionLease,
) {
    private val started = AtomicBoolean(false)
    private val coordinator = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "market-history-coordinator").apply { isDaemon = true }
    }
    private val heartbeat = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "market-history-lease").apply { isDaemon = true }
    }
    private val lockKey get() = "${properties.redisKeyPrefix}:collection:pc"
    private val intervalMillis get() = TimeUnit.HOURS.toMillis(properties.statistics.intervalHours)

    @EventListener(ApplicationReadyEvent::class)
    fun onReady() {
        if (properties.statistics.enabled && started.compareAndSet(false, true)) {
            coordinator.schedule(::collect, properties.statistics.startupDelaySeconds, TimeUnit.SECONDS)
        }
    }

    private fun syncAt(day: LocalDate): Long = day
        .atTime(properties.statistics.dailySyncUtcHour, properties.statistics.dailySyncUtcMinute)
        .toInstant(ZoneOffset.UTC).toEpochMilli()

    private fun collect() {
        var delay = intervalMillis
        try {
            val now = System.currentTimeMillis()
            val missingPublication = publications.current() == null
            val savedNextAt = lease.nextRoundAt(lockKey)
            // 标记丢失时越过昨日成功写下的“明天再同步”，但保留故障重试冷却。
            val recoverMissing = missingPublication && savedNextAt - now > intervalMillis
            val nextAt = if (recoverMissing) now else maxOf(
                savedNextAt, if (missingPublication) now else syncAt(LocalDate.now(ZoneOffset.UTC))
            )
            if (nextAt > now) delay = nextAt - now
            else {
                runRound(recoverMissing)
                delay = (lease.nextRoundAt(lockKey) - System.currentTimeMillis()).takeIf { it > 0 } ?: intervalMillis
            }
        } catch (error: Exception) {
            if (error is InterruptedException) Thread.currentThread().interrupt()
            logError("Warframe Market 成交数据同步失败，保留已提交批次和旧榜，稍后重试覆盖未发布日", error)
        } finally {
            if (!coordinator.isShutdown && !Thread.currentThread().isInterrupted) {
                // 等待日更时仍轻量检查 Redis 标记，以便标记丢失后恢复；不会重复下载已发布日。
                coordinator.schedule(::collect, delay.coerceIn(1_000, intervalMillis), TimeUnit.MILLISECONDS)
            }
        }
    }

    private fun runRound(recoverMissingPublication: Boolean) {
        val owner = UUID.randomUUID().toString()
        if (!lease.acquire(lockKey, owner)) return
        val owned = AtomicBoolean(true)
        var renewal: java.util.concurrent.ScheduledFuture<*>? = null
        try {
            if (!lease.beginRound(lockKey, owner, System.currentTimeMillis(), recoverMissingPublication)) return
            val current = publications.current()
            val today = LocalDate.now(ZoneOffset.UTC)
            // 标记丢失且今日归档尚未到发布时间时，重新导入前一份已可用的完整日。
            val tradeDay = today.minusDays(if (current == null && System.currentTimeMillis() < syncAt(today)) 2 else 1)
            var nextAt = System.currentTimeMillis() + intervalMillis
            renewal = heartbeat.scheduleAtFixedRate({
                try {
                    if (!lease.renew(lockKey, owner)) owned.set(false)
                } catch (error: Exception) {
                    owned.set(false)
                    logError("排行榜同步锁续租失败，取消当前导入", error)
                }
            }, 20, 20, TimeUnit.SECONDS)
            fun ensureLease() {
                if (Thread.currentThread().isInterrupted) throw InterruptedException("排行榜同步已取消")
                check(owned.get() && lease.isOwner(lockKey, owner)) { "排行榜同步锁已失效" }
            }
            try {
                ensureLease()
                if (current == null || current.tradeDay < tradeDay) {
                    val itemIds = syncCatalogIds(::ensureLease)
                    val imported = tradeSource.fetch(tradeDay, itemIds) { batch ->
                        tradeStore.saveTradeBatch(batch, ::ensureLease)
                    }
                    val publication = tradeStore.finalizeImport(tradeDay, imported, ::ensureLease)
                    publications.publish(publication, lockKey, owner)
                    logInfo("Warframe Market 成交数据 $tradeDay 已发布：${publication.itemCount} 件有成交物品、${publication.recordCount} 条规格记录；归档中 ${imported.unmatchedItems} 件物品未匹配目录")
                }
                nextAt = syncAt(tradeDay.plusDays(2)).coerceAtLeast(System.currentTimeMillis() + 1_000)
                // 只清理已提交后的旧日数据；清理中断不影响当前榜单，下一轮可继续。
                val publishedDay = publications.current()?.tradeDay ?: error("归档导入后缺少Redis发布标记")
                cleanExpired(publishedDay, ::ensureLease)
            } finally {
                if (owned.get() && !Thread.currentThread().isInterrupted) lease.finishRound(lockKey, owner, nextAt)
            }
        } finally {
            owned.set(false)
            renewal?.cancel(false)
            lease.release(lockKey, owner)
        }
    }

    /** 完整目录只在此作用域保留，读取归档期间仅保留 ID 集合。 */
    private fun syncCatalogIds(ensureLease: () -> Unit): Set<String> {
        ensureLease()
        val itemList = catalog.getMarketItems()
        require(itemList.isNotEmpty() && itemList.all { !it.id.isNullOrBlank() && !it.urlName.isNullOrBlank() }) {
            "WM 返回了不完整的物品目录"
        }
        itemList.asSequence().chunked(MarketDailyTradeSource.BATCH_SIZE).forEach { batch ->
            tradeStore.saveCatalogBatch(batch, ensureLease)
        }
        return itemList.mapTo(HashSet(itemList.size)) { it.id!! }
    }

    private fun cleanExpired(day: LocalDate, ensureLease: () -> Unit) {
        val previousDay = publications.previousDay() ?: day.minusDays(1)
        while (true) {
            ensureLease()
            if (tradeStore.deleteExpiredBatch(day, previousDay) == 0) break
        }
    }

    @PreDestroy
    fun close() {
        coordinator.shutdownNow()
        heartbeat.shutdownNow()
    }
}