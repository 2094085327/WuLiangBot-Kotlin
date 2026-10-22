package bot.wuliang.scheduling

import bot.wuliang.botLog.logUtil.LoggerUtils.logError
import bot.wuliang.botLog.logUtil.LoggerUtils.logInfo
import bot.wuliang.entity.WfMarketItemEntity
import bot.wuliang.httpUtil.HttpUtil
import bot.wuliang.market.MarketCatalogSource
import bot.wuliang.market.MarketProperties
import bot.wuliang.market.history.*
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.*
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.annotation.PreDestroy

/** 每天只采集上一完整 UTC 日的关闭订单；失败恢复逐商品进度，完成后等待下个同步日。 */
@Component
class MarketStatisticsCollector(
    private val properties: MarketProperties,
    private val catalog: MarketCatalogSource,
    private val store: MarketCollectionStore,
    private val tradeSource: MarketDailyTradeSource,
    private val tradeStore: MarketDailyTradeStore,
    private val publications: MarketTradePublicationService,
    private val lease: MarketCollectionLease,
) {
    private val started = AtomicBoolean(false)
    private val coordinator = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(
            task,
            "wm-statistics-coordinator"
        ).apply { isDaemon = true }
    }
    private val workers = Executors.newFixedThreadPool(properties.statistics.workers) { task ->
        Thread(
            task,
            "wm-statistics-worker"
        ).apply { isDaemon = true }
    }
    private val heartbeat = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "wm-statistics-lease").apply {
            isDaemon = true
        }
    }
    private val lockKey get() = "${properties.redisKeyPrefix}:collection:${properties.statistics.platform}"
    private val intervalMillis get() = TimeUnit.HOURS.toMillis(properties.statistics.intervalHours)

    @EventListener(ApplicationReadyEvent::class)
    fun onReady() {
        if (properties.statistics.enabled && started.compareAndSet(false, true)) {
            coordinator.schedule(::collect, properties.statistics.startupDelaySeconds, TimeUnit.SECONDS)
        }
    }

    /** 本轮退出后才安排下一次，避免异步提交导致 fixedDelay 从提交时开始计算。 */
    private fun collect() {
        var delay = intervalMillis
        try {
            if (!properties.statistics.enabled) return
            val remaining = lease.nextRoundAt(lockKey) - System.currentTimeMillis()
            if (remaining > 0) delay = remaining
            else {
                val scheduled = LocalDate.now(ZoneOffset.UTC).atTime(properties.statistics.dailySyncUtcHour, 0)
                    .toInstant(ZoneOffset.UTC).toEpochMilli()
                if (scheduled > System.currentTimeMillis()) delay = scheduled - System.currentTimeMillis()
                else {
                    runRound()
                    delay =
                        (lease.nextRoundAt(lockKey) - System.currentTimeMillis()).takeIf { it > 0 } ?: intervalMillis
                }
            }
        } catch (error: Exception) {
            if (error is InterruptedException) Thread.currentThread().interrupt()
            logError("WM 行情采集终止，已成功的商品进度已保留", error)
        } finally {
            if (!coordinator.isShutdown && !Thread.currentThread().isInterrupted) {
                coordinator.schedule(::collect, delay.coerceAtLeast(1_000), TimeUnit.MILLISECONDS)
            }
        }
    }

    private fun runRound() {
        val owner = UUID.randomUUID().toString()
        if (!lease.acquire(lockKey, owner)) return
        val owned = AtomicBoolean(true)
        var renewal: java.util.concurrent.ScheduledFuture<*>? = null
        try {
            if (!lease.beginRound(lockKey, owner, System.currentTimeMillis())) return
            val tradeDay = LocalDate.now(ZoneOffset.UTC).minusDays(1)
            var nextAt: Long? = null
            renewal = heartbeat.scheduleAtFixedRate({
                try {
                    if (!lease.renew(lockKey, owner)) owned.set(false)
                } catch (error: Exception) {
                    owned.set(false)
                    logError("WM 行情采集失败，停止后续派发", error)
                }
            }, 20, 20, TimeUnit.SECONDS)
            val roundStart = System.nanoTime()
            fun ensureLease() {
                if (Thread.currentThread().isInterrupted) throw InterruptedException("WM 行情采集已取消")
                check(owned.get() && lease.isOwner(lockKey, owner)) { "WM 行情采集锁已失效" }
                check(System.nanoTime() - roundStart < TimeUnit.HOURS.toNanos(properties.statistics.maxRoundHours)) {
                    "WM 本轮采集已达时长上限，余下商品等待下个周期"
                }
            }
            try {
                ensureLease()
                val previousPublication = publications.current()
                val itemList = catalog.getMarketItems()
                require(itemList.isNotEmpty() && itemList.all { !it.id.isNullOrBlank() && !it.urlName.isNullOrBlank() }) {
                    "WM 返回了不完整的物品目录"
                }
                ensureLease()
                store.syncCatalog(itemList)
                val byId = itemList.associateBy { it.id!! }
                store.prepareDay(byId.keys.toList(), tradeDay)
                // 未能恢复发布标记时不猜测最大日期，也不删除可能仍用于旧榜的数据。
                if (previousPublication != null) cleanExpired(tradeDay, previousPublication, ::ensureLease)
                logInfo("WM 每日成交采集：目录 ${itemList.size} 件，目标UTC日 $tradeDay，只保存最低/最高价、加权均价与成交量")
                collectSource(byId, ::ensureLease, tradeDay) { item ->
                    val records = tradeSource.fetch(item, tradeDay)
                    ensureLease()
                    tradeStore.save(requireNotNull(item.id), tradeDay, records)
                }
                ensureLease()
                val progress = store.progress(tradeDay)
                val published = publications.publish(tradeDay, lockKey, owner)
                if (published != null) {
                    cleanExpired(tradeDay, published, ::ensureLease)
                    logInfo("WM 排行榜发布日 ${published.tradeDay}：成功处理 ${published.processedItems}/${published.totalItems}，缺项 ${published.pendingItems}")
                } else {
                    logInfo("WM $tradeDay 未达99%成功处理率或无有效成交，继续展示旧榜；成功 ${progress.processedItems}/${progress.totalItems}")
                }
                val retryable =
                    store.dueDailyItemIds(properties.statistics.platform, MarketDailyTradeAverage.SOURCE, tradeDay)
                        .any(byId::containsKey)
                if (!retryable) {
                    nextAt = tradeDay.plusDays(2).atTime(properties.statistics.dailySyncUtcHour, 0)
                        .toInstant(ZoneOffset.UTC).toEpochMilli().coerceAtLeast(System.currentTimeMillis() + 1_000)
                } else {
                    logInfo("WM $tradeDay 仍有可重试商品，稍后单独补采；当日已达上限 ${progress.exhaustedItems} 件，不再请求")
                }
            } finally {
                // 上游故障和时间预算耗尽也必须休息；进程崩溃保留轮次以便恢复。
                if (owned.get() && !Thread.currentThread().isInterrupted) {
                    lease.finishRound(lockKey, owner, nextAt ?: (System.currentTimeMillis() + intervalMillis))
                }
            }
            logInfo("WM 每日成交本轮结束；仅未成功且未达当日上限的商品继续重试")
        } finally {
            owned.set(false)
            renewal?.cancel(false)
            lease.release(lockKey, owner)
        }
    }

    private fun cleanExpired(day: LocalDate, published: MarketTradePublication, ensureLease: () -> Unit) {
        check(published.tradeDay <= day) { "WM发布日超前，暂停清理数据" }
        val preserveDay = if (published.tradeDay < day) published.tradeDay
        else tradeStore.previousDataDay(day) ?: day.minusDays(1)
        while (true) {
            ensureLease()
            if (tradeStore.deleteExpiredBatch(day, preserveDay) < 5000) break
            Thread.sleep(25)
        }
    }

    private fun collectSource(
        byId: Map<String, WfMarketItemEntity>,
        ensureLease: () -> Unit,
        dataDay: LocalDate,
        fetchAndSave: (WfMarketItemEntity) -> Unit,
    ) {
        val sourceId = MarketDailyTradeAverage.SOURCE
        if (lease.isSourcePaused(lockKey, sourceId)) {
            logInfo("WM 数据源 $sourceId 仍在故障冷却，跳过本轮")
            return
        }
        val due = store.dueDailyItemIds(properties.statistics.platform, sourceId, dataDay).mapNotNull(byId::get)
        val cursor = AtomicInteger(0)
        val success = AtomicInteger(0)
        val failed = AtomicInteger(0)
        val consecutive = AtomicInteger(0)
        val paused = AtomicBoolean(false)
        val jobs = (1..properties.statistics.workers).map {
            workers.submit {
                while (!paused.get() && !Thread.currentThread().isInterrupted) {
                    val item = due.getOrNull(cursor.getAndIncrement()) ?: break
                    ensureLease()
                    if (!store.markAttempt(item.id!!, sourceId, dataDay)) continue
                    try {
                        fetchAndSave(item)
                        consecutive.set(0)
                        if (success.incrementAndGet() % 100 == 0) {
                            logInfo("WM $sourceId 进度：成功 ${success.get()}，失败 ${failed.get()}，总计 ${due.size}")
                        }
                    } catch (error: Exception) {
                        if (error is InterruptedException) {
                            Thread.currentThread().interrupt()
                            throw error
                        }
                        ensureLease()
                        store.markFailure(item.id, sourceId, error)
                        failed.incrementAndGet()
                        val http = error as? HttpUtil.HttpException
                        val deprecated = http?.responseBody?.contains("deprecated", ignoreCase = true) == true
                        // 商品不存在/不支持统计只消耗该商品的每日额度，避免少量坏条目暂停全目录。
                        val consecutiveFailures = if (http?.statusCode in setOf(400, 404)) {
                            consecutive.set(0)
                            0
                        } else consecutive.incrementAndGet()
                        if (deprecated || http?.statusCode in setOf(403, 429, 509) ||
                            consecutiveFailures >= properties.statistics.maxConsecutiveFailures
                        ) {
                            if (paused.compareAndSet(false, true)) {
                                lease.pauseSource(
                                    lockKey,
                                    sourceId,
                                    Duration.ofHours(if (deprecated) 24 else properties.statistics.intervalHours)
                                )
                                logError("WM 数据源 $sourceId 暂停后续采集，保留旧数据：${item.urlName}", error)
                            }
                        } else logError("WM $sourceId 采集失败：${item.urlName}", error)
                    }
                }
            }
        }
        try {
            var failure: Throwable? = null
            jobs.forEach { job ->
                try {
                    job.get()
                } catch (error: ExecutionException) {
                    paused.set(true)
                    if (failure == null) failure = error.cause ?: error
                }
            }
            failure?.let { throw IllegalStateException("WM $sourceId 工作线程终止", it) }
        } catch (error: InterruptedException) {
            jobs.forEach { if (!it.isDone) it.cancel(true) }
            Thread.currentThread().interrupt()
            throw error
        } finally {
            paused.set(true)
        }
        logInfo("WM $sourceId 完成：成功 ${success.get()}，失败 ${failed.get()}，待采 ${due.size}")
    }

    @PreDestroy
    fun close() {
        coordinator.shutdownNow()
        workers.shutdownNow()
        heartbeat.shutdownNow()
    }
}
