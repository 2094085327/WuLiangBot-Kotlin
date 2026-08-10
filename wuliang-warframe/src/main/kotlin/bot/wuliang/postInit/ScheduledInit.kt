package bot.wuliang.postInit

import bot.wuliang.botLog.logUtil.LoggerUtils.logError
import bot.wuliang.botLog.logUtil.LoggerUtils.logInfo
import bot.wuliang.config.WARFRAME_DATA
import bot.wuliang.config.WARFRAME_STATUS_URL
import bot.wuliang.httpUtil.HttpUtil
import bot.wuliang.updateResources.UpdateResourcesUtil
import bot.wuliang.utils.ParseDataUtil
import bot.wuliang.utils.WfUtil
import kotlinx.coroutines.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class ScheduledInit {

    @Autowired
    private lateinit var parseDataUtil: ParseDataUtil

    @Autowired
    private lateinit var wfUtil: WfUtil

    private val updateResources = UpdateResourcesUtil()

    /**
     * 应用启动后补充刷新周常数据并缓存本周图片，避免周一定时任务因停机等原因未执行
     */
    @EventListener(ApplicationReadyEvent::class)
    fun initWeeklyImageCache() {
        CoroutineScope(Dispatchers.IO).launch {
            runCatching { refreshWeeklyDataAndCache() }
                .onSuccess { logInfo("周常数据刷新及图片缓存初始化完成") }
                .onFailure { logError("周常数据刷新及图片缓存初始化失败", it) }
        }
    }

    @Scheduled(cron = "1 0 8 * * 1")
    fun weeklyInit() = runBlocking {
        refreshWeeklyDataAndCache()
    }

    private suspend fun refreshWeeklyDataAndCache() = coroutineScope {
        val data = HttpUtil.doGetJson(WARFRAME_STATUS_URL)
        updateResources.waitForResources("$WARFRAME_DATA/sortieData.json")

        val dataJobs = listOf(
            // 每周突击
            launch(Dispatchers.IO) { parseDataUtil.parseArchonHunt(data["LiteSorties"]) },
            // 钢铁之路
            launch(Dispatchers.IO) { parseDataUtil.parseSteelPath() },
            // 午夜电波
            launch(Dispatchers.IO) { parseDataUtil.parseNightWave(data["SeasonInfo"]) },
            // 虚空商人
            launch(Dispatchers.IO) { parseDataUtil.parseVoidTraders(data["VoidTraders"]) }
        )

        // 紫卡和回廊顺序执行
        parseDataUtil.parseWeeklyRiven()
        parseDataUtil.parseIncarnon()
        dataJobs.joinAll()
        runCatching { wfUtil.getWeeklyImgUrl() }
            .onFailure { logError("周常图片预缓存失败", it) }
    }

    @Scheduled(cron = "1 0 8 * * *")
    fun dailyInit() = runBlocking {
        val data = HttpUtil.doGetJson(WARFRAME_STATUS_URL)
        updateResources.waitForResources("$WARFRAME_DATA/sortieData.json")

        // 每日突击
        launch(Dispatchers.IO) { parseDataUtil.parseSorties(data["Sorties"]) }
        // 结合仪式
        launch(Dispatchers.IO) { parseDataUtil.parseSimaris(data["LibraryInfo"]) }
    }
}