package bot.wuliang.command

import bot.wuliang.adapter.context.ExecutionContext
import bot.wuliang.config.WfMarketConfig.WF_CALENDAR_KEY
import bot.wuliang.config.WfMarketConfig.WF_CONQUEST_KEY
import bot.wuliang.distribute.annotation.AParameter
import bot.wuliang.distribute.annotation.ActionService
import bot.wuliang.distribute.annotation.Executor
import bot.wuliang.imageProcess.WebImgUtil
import bot.wuliang.logAop.SystemLog
import bot.wuliang.redis.RedisService
import bot.wuliang.translation.PublicExportService
import bot.wuliang.worldstate.presentation.WeeklyImageService
import bot.wuliang.worldstate.service.WeeklyQueryService
import bot.wuliang.worldstate.source.WorldStateSource
import org.springframework.stereotype.Component
import java.util.*

/** 科研、日历与周常图片指令，查询与图片缓存各自独立。 */
@Component
@ActionService
class WeeklyCommands(
    private val weeklyImageService: WeeklyImageService,
    private val weeklyQueryService: WeeklyQueryService,
    private val worldStateSource: WorldStateSource,
    private val translations: PublicExportService,
    private val webImgUtil: WebImgUtil,
    private val redisService: RedisService,
) {

    /** 准备当前科研任务缓存并发送科研图片，词库未就绪时返回初始化提示。 */
    @SystemLog(businessName = "获取科研任务信息")
    @AParameter
    @Executor(action = "\\b(科研|时光科研|深层科研)\\b")
    suspend fun getConquest(context: ExecutionContext) {
        if (!translations.ready) {
            context.sender.sendText(PublicExportService.UNAVAILABLE_MESSAGE)
            return
        }
        if (!redisService.hasKey(translations.cacheKey(WF_CONQUEST_KEY))) {
            val data = worldStateSource.get()
            weeklyQueryService.refreshConquests(data["Conquests"])
        }

        val imgData = WebImgUtil.ImgData(
            url = "http://${webImgUtil.frontendAddress}/conquest",
            imgName = "conquest-${UUID.randomUUID()}",
            element = "#app",
            waitElement = ".warframeConquest"
        )

        val url = webImgUtil.getImgUrl(imgData)
        context.sender.sendImage(url)
        webImgUtil.deleteImg(imgData)
    }

    /** 准备当前 1999 日历缓存并发送日历图片。 */
    @SystemLog(businessName = "获取1999日历信息")
    @AParameter
    @Executor(action = "\\b(日历|1999日历|石榴2代|石榴电脑)\\b")
    suspend fun getCalendar(context: ExecutionContext) {
        if (!translations.ready) {
            context.sender.sendText(PublicExportService.UNAVAILABLE_MESSAGE)
            return
        }
        if (!redisService.hasKey(translations.cacheKey(WF_CALENDAR_KEY))) {
            val data = worldStateSource.get()
            weeklyQueryService.getCalendar(data["KnownCalendarSeasons"])
        }

        val imgData = WebImgUtil.ImgData(
            url = "http://${webImgUtil.frontendAddress}/calendar",
            imgName = "calendar-${UUID.randomUUID()}",
            element = "#app",
            waitElement = ".warframeCalendar"
        )

        val url = webImgUtil.getImgUrl(imgData)
        context.sender.sendImage(url)
        webImgUtil.deleteImg(imgData)
    }

    /** 通过周常图片服务预加载必要数据并发送本周汇总图片。 */
    @SystemLog(businessName = "获取周常任务信息")
    @AParameter
    @Executor(action = "\\b(周常|每周任务)\\b")
    suspend fun getWeekly(context: ExecutionContext) {
        if (!translations.ready) {
            context.sender.sendText(PublicExportService.UNAVAILABLE_MESSAGE)
            return
        }
        context.sender.sendImage(weeklyImageService.imageUrl())
    }
}