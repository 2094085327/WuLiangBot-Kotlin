package bot.wuliang.scheduling

import bot.wuliang.botLog.logUtil.LoggerUtils.logError
import bot.wuliang.botLog.logUtil.LoggerUtils.logInfo
import bot.wuliang.entity.WfRivenEntity
import bot.wuliang.mapper.WfRivenMapper
import bot.wuliang.market.MarketRequestPriority
import bot.wuliang.riven.RivenRankingService
import bot.wuliang.riven.samples.*
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import javax.annotation.PreDestroy

/** 预热周榜前若干武器。 */
@Component
class RivenSampleMaintenance(
    private val properties: RivenSampleProperties,
    private val rankings: RivenRankingService,
    private val weapons: WfRivenMapper,
    private val source: RivenSampleSource,
    private val store: RivenSampleStore,
    private val warmup: RivenWarmupState,
) {
    private val executor = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "riven-sample-maintenance").apply { isDaemon = true }
    }
    private val started = AtomicBoolean(false)
    private var catalogDay: LocalDate? = null
    private var catalog = emptyList<WfRivenEntity>()
    private var pruneAfter = 0L
    private var warmupAfter = 0L

    @EventListener(ApplicationReadyEvent::class)
    fun start() {
        if (properties.enabled && started.compareAndSet(false, true)) {
            executor.scheduleWithFixedDelay(::tick, properties.startupDelaySeconds, 60, TimeUnit.SECONDS)
        }
    }

    private fun tick() {
        try {
            if (!properties.enabled) return
            val now = System.currentTimeMillis()
            if (now >= pruneAfter) {
                store.prune(now)
                pruneAfter = now + TimeUnit.MINUTES.toMillis(10)
            }
            if (!properties.warmup.enabled || now < warmupAfter) return
            val day = LocalDate.now(ZoneOffset.UTC)
            warmupAfter = maxOf(warmup.readyAfter(day), rankings.warmupStartsAt(day))
            if (now < warmupAfter || source.paused()) return
            val progress = warmup.progress(day)
            if (warmup.exhausted(progress)) {
                warmupAfter = warmup.dayEnd(day)
                return
            }
            if (now < warmup.nextRequestAt(progress)) {
                warmupAfter = warmup.nextRequestAt(progress)
                return
            }
            if (!rankings.readyForWarmup(now)) {
                warmupAfter = minOf(warmup.dayEnd(day), now + TimeUnit.MINUTES.toMillis(10))
                return
            }
            if (catalogDay != day) {
                catalog = weapons.selectAllRiven()
                catalogDay = day
            }
            val ranked = rankings.popularWeapons(catalog).take(properties.warmup.weaponLimit)
            check(ranked.isNotEmpty()) { "紫卡热门武器映射数量异常" }
            val freshness = ranked.mapIndexed { index, weapon ->
                weapon to TimeUnit.HOURS.toMillis(
                    if (index < properties.warmup.hotWeaponCount) properties.warmup.hotRefreshHours
                    else properties.warmup.refreshHours
                )
            }.toMap()
            var nextCheck = warmup.dayEnd(day)
            val pending = ranked.filter { weapon ->
                if (warmup.attempted(progress, weapon)) false
                else {
                    val until = warmup.retryAfter(progress, weapon)
                    if (until > now) nextCheck = minOf(nextCheck, until)
                    until <= now
                }
            }
            val query = RivenSampleQuery()
            for (window in pending.chunked(50)) {
                val keys = window.associateWith { source.queryKey(it, query) }
                val observations = store.warmupObservations(keys.values.toList())
                for (weapon in window) {
                    val freshFor = freshness.getValue(weapon)
                    val observation = observations[keys.getValue(weapon)]
                    val freshUntil = (observation?.lastSuccessAt ?: 0L) + freshFor
                    if (freshUntil > now) {
                        warmup.complete(day, weapon, freshUntil)
                        nextCheck = minOf(nextCheck, freshUntil)
                        continue
                    }
                    if (observation != null && observation.retryAfter > now) {
                        warmup.defer(day, weapon, observation.retryAfter)
                        nextCheck = minOf(nextCheck, observation.retryAfter)
                        continue
                    }
                    var reservationChecked = false
                    var checkedUntil = 0L
                    try {
                        val result = source.search(
                            weapon, query, "WARMUP", MarketRequestPriority.BACKGROUND,
                            beforeFetch = {
                                // 在查询锁内重读，复用期间其他实例或用户完成的同范围查询。
                                val latest = store.observation(keys.getValue(weapon))
                                val checkNow = System.currentTimeMillis()
                                val latestFreshUntil = (latest?.lastSuccessAt ?: 0L) + freshFor
                                when {
                                    latestFreshUntil > checkNow -> {
                                        checkedUntil = latestFreshUntil
                                        warmup.complete(day, weapon, checkedUntil)
                                        false
                                    }

                                    latest != null && latest.retryAfter > checkNow -> {
                                        checkedUntil = latest.retryAfter
                                        warmup.defer(day, weapon, checkedUntil)
                                        false
                                    }

                                    else -> {
                                        reservationChecked = true
                                        warmup.reserve(day, weapon)
                                    }
                                }
                            })
                        if (result != null) {
                            val until = result.observedAt + freshFor
                            warmup.complete(day, weapon, until)
                            nextCheck = minOf(nextCheck, until)
                        }
                        if (result?.fetched == true) {
                            logInfo("紫卡热门预热：$weapon")
                            return
                        }
                        // 原子检查拒绝额度或间隔时，直接让出；缓存命中则继续补位。
                        if (result == null && reservationChecked) return
                        if (result == null) {
                            val until = checkedUntil.takeIf { it > now } ?: (now + TimeUnit.MINUTES.toMillis(1))
                            warmup.defer(day, weapon, until)
                            nextCheck = minOf(nextCheck, until)
                        }
                    } catch (error: Exception) {
                        // 已占额度的不再尝试；发送前失败只延后，不占用每日名额。
                        warmup.defer(
                            day, weapon, System.currentTimeMillis() +
                                    TimeUnit.MINUTES.toMillis(properties.failureCooldownMinutes)
                        )
                        throw error
                    }
                }
            }
            // 等到最早到期的热门武器；不补跑错过的轮次，不向榜单之外扩展。
            warmupAfter = nextCheck
        } catch (error: Exception) {
            if (error is InterruptedException) Thread.currentThread().interrupt()
            logError("紫卡预热跳过本次执行，保留已有样本", error)
        }
    }

    @PreDestroy
    fun stop() {
        executor.shutdownNow()
    }
}