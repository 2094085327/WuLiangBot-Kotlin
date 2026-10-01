package bot.wuliang.postInit

import bot.wuliang.botLog.logUtil.LoggerUtils.logError
import bot.wuliang.botLog.logUtil.LoggerUtils.logInfo
import bot.wuliang.config.WARFRAME_STATUS_URL
import bot.wuliang.httpUtil.HttpUtil
import bot.wuliang.translation.PublicExportService
import bot.wuliang.utils.ParseDataUtil
import bot.wuliang.utils.WfUtil
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
    private lateinit var parseDataUtil: ParseDataUtil

    @Autowired
    private lateinit var wfUtil: WfUtil

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

    @Scheduled(cron = "1 0 8 * * 1", zone = "Asia/Shanghai")
    fun weeklyInit() = runBlocking {
        refreshWeeklyDataAndCache()
    }

    private suspend fun refreshWeeklyDataAndCache() = coroutineScope {
        if (!translations.ready) return@coroutineScope
        val data = HttpUtil.doGetJson(WARFRAME_STATUS_URL)

        val dataJobs = listOf(
            // 钢铁之路
            launch(Dispatchers.IO) { parseDataUtil.parseSteelPath() },
            // 虚空商人
            launch(Dispatchers.IO) { parseDataUtil.parseVoidTraders(data["VoidTraders"]) }
        )

        // 紫卡和回廊顺序执行
        parseDataUtil.parseWeeklyRiven()
        parseDataUtil.parseIncarnon()
        dataJobs.joinAll()
        // 上游跨周发布可能晚于定时器，失败后重新获取快照再试，不能缓存缺项图片。
        for (attempt in 1..3) {
            try {
                wfUtil.getWeeklyImgUrl(if (attempt == 1) data else HttpUtil.doGetJson(WARFRAME_STATUS_URL))
                break
            } catch (error: Exception) {
                if (error is CancellationException || attempt == 3) throw error
                logError("周常图片预缓存失败，一分钟后重试（$attempt/3）", error)
                delay(60_000.milliseconds)
            }
        }
    }

    @Scheduled(cron = "1 0 8 * * *")
    fun dailyInit() = runBlocking {
        if (!translations.ready) return@runBlocking
        val data = HttpUtil.doGetJson(WARFRAME_STATUS_URL)

        // 每日突击
        launch(Dispatchers.IO) { parseDataUtil.parseSorties(data["Sorties"]) }
        // 结合仪式
        launch(Dispatchers.IO) { parseDataUtil.parseSimaris(data["LibraryInfo"]) }
    }
}
