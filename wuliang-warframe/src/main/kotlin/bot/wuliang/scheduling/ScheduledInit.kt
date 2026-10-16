package bot.wuliang.scheduling

import bot.wuliang.botLog.logUtil.LoggerUtils.logError
import bot.wuliang.botLog.logUtil.LoggerUtils.logInfo
import bot.wuliang.riven.RivenRankingService
import bot.wuliang.translation.PublicExportService
import bot.wuliang.worldstate.presentation.WeeklyImageService
import bot.wuliang.worldstate.service.LocalRotationService
import bot.wuliang.worldstate.service.MissionQueryService
import bot.wuliang.worldstate.service.TraderQueryService
import bot.wuliang.worldstate.source.WorldStateSource
import kotlinx.coroutines.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import kotlin.time.Duration.Companion.milliseconds

@Component
class ScheduledInit {
    @Autowired
    private lateinit var weeklyImageService: WeeklyImageService

    @Autowired
    private lateinit var rivenRankingService: RivenRankingService

    @Autowired
    private lateinit var localRotationService: LocalRotationService

    @Autowired
    private lateinit var missionQueryService: MissionQueryService

    @Autowired
    private lateinit var traderQueryService: TraderQueryService

    @Autowired
    private lateinit var worldStateSource: WorldStateSource

    @Autowired
    private lateinit var translations: PublicExportService

    /**
     * 应用启动后补充刷新周常数据并缓存本周图片，避免周一定时任务因停机等原因未执行
     */
    @EventListener(ApplicationReadyEvent::class)
    fun initWeeklyImageCache() {
        if (!translations.ready) return
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { refreshWeeklyDataAndCache() }
                .onSuccess { logInfo("周常数据刷新及图片缓存初始化完成") }
                .onFailure { logError("周常数据刷新及图片缓存初始化失败", it) }
        }
    }

    /** 执行周一定时刷新，等待周常数据及图片预缓存流程完成。 */
    @Scheduled(cron = "1 0 8 * * 1", zone = "Asia/Shanghai")
    fun weeklyInit() = runBlocking {
        refreshWeeklyDataAndCache()
    }

    /** 刷新钢路、商人、紫卡周榜及回廊数据，再预缓存周常图片；上游发布延迟时按限制次数重试。 */
    private suspend fun refreshWeeklyDataAndCache() = coroutineScope {
        if (!translations.ready) return@coroutineScope
        val data = worldStateSource.get()

        val dataJobs = listOf(
            // 钢铁之路
            launch(Dispatchers.IO) { localRotationService.getSteelPath() },
            // 虚空商人
            launch(Dispatchers.IO) { traderQueryService.getVoidTraders(data["VoidTraders"]) }
        )

        // 紫卡和回廊顺序执行
        rivenRankingService.refreshRanking()
        localRotationService.getIncarnon()
        dataJobs.joinAll()
        // 上游跨周发布可能晚于定时器；重试时将缓存时限缩短至一分钟，避免复用上一轮快照。
        // 缺项时继续报错，不能将不完整的周常图片写入缓存。
        for (attempt in 1..3) {
            try {
                weeklyImageService.imageUrl(if (attempt == 1) data else worldStateSource.get(maxAgeMillis = 60_000))
                break
            } catch (error: Exception) {
                if (error is CancellationException || attempt == 3) throw error
                logError("周常图片预缓存失败，一分钟后重试（$attempt/3）", error)
                delay(60_000.milliseconds)
            }
        }
    }

    /** 执行每日刷新，在词库就绪后并发更新突击和结合仪式目标缓存。 */
    @Scheduled(cron = "1 0 8 * * *")
    fun dailyInit() = runBlocking {
        if (!translations.ready) return@runBlocking
        val data = worldStateSource.get()

        // 每日突击
        launch(Dispatchers.IO) { missionQueryService.refreshSortie(data["Sorties"]) }
        // 结合仪式
        launch(Dispatchers.IO) { localRotationService.getSimaris(data["LibraryInfo"]) }
    }
}