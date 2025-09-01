package bot.wuliang.postInit

import bot.wuliang.config.WARFRAME_DATA
import bot.wuliang.config.WARFRAME_STATUS_URL
import bot.wuliang.httpUtil.HttpUtil
import bot.wuliang.updateResources.UpdateResourcesUtil
import bot.wuliang.utils.ParseDataUtil
import kotlinx.coroutines.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
class ScheduledInit {

    @Autowired
    private lateinit var parseDataUtil: ParseDataUtil

    private val updateResources = UpdateResourcesUtil()

    @Scheduled(cron = "1 0 8 * * 1")
    fun weeklyInit() = runBlocking {
        val data = HttpUtil.doGetJson(WARFRAME_STATUS_URL)
        updateResources.waitForResources("$WARFRAME_DATA/sortieData.json")

        // 每周突击
        launch(Dispatchers.IO) { parseDataUtil.parseArchonHunt(data["LiteSorties"]) }
        // 钢铁之路
        launch(Dispatchers.IO) { parseDataUtil.parseSteelPath() }
        // 午夜电波
        launch(Dispatchers.IO) { parseDataUtil.parseNightWave(data["SeasonInfo"]) }
        // 虚空商人
        launch(Dispatchers.IO) { parseDataUtil.parseVoidTraders(data["VoidTraders"]) }
        // 每周紫卡数据
        launch(Dispatchers.IO) { parseDataUtil.parseWeeklyRiven() }
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