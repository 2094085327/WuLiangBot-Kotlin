package bot.wuliang.command

import bot.wuliang.adapter.context.ExecutionContext
import bot.wuliang.config.WfMarketConfig.WF_ARCHONHUNT_KEY
import bot.wuliang.config.WfMarketConfig.WF_INVASIONS_KEY
import bot.wuliang.config.WfMarketConfig.WF_NIGHTWAVE_KEY
import bot.wuliang.config.WfMarketConfig.WF_SORTIE_KEY
import bot.wuliang.distribute.annotation.AParameter
import bot.wuliang.distribute.annotation.ActionService
import bot.wuliang.distribute.annotation.Executor
import bot.wuliang.imageProcess.WebImgUtil
import bot.wuliang.logAop.SystemLog
import bot.wuliang.redis.RedisService
import bot.wuliang.translation.PublicExportService
import bot.wuliang.worldstate.service.MissionQueryService
import bot.wuliang.worldstate.source.WorldStateSource
import org.springframework.stereotype.Component
import java.util.*
import java.util.regex.Matcher

/** 实时任务指令入口，调用查询服务准备页面数据并发送对应图片。 */
@Component
@ActionService
class MissionCommands(
    private val missionQueryService: MissionQueryService,
    private val worldStateSource: WorldStateSource,
    private val translations: PublicExportService,
    private val webImgUtil: WebImgUtil,
    private val redisService: RedisService,
) {

    /** 根据匹配到的裂缝类型预加载页面数据，生成普通、钢铁之路或九重天裂缝图片并发送。 */
    @SystemLog(businessName = "获取裂缝信息")
    @AParameter
    @Executor(action = "\\b(裂缝|裂隙|钢铁裂缝|钢铁裂隙|九重天)\\b")
    suspend fun getFissures(context: ExecutionContext, matcher: Matcher) {
        if (!translations.ready) {
            context.sender.sendText(PublicExportService.UNAVAILABLE_MESSAGE)
            return
        }
        val fissureType = matcher.group(1)
        // 根据不同的裂缝类型构造图片的 URL
        val urlSuffix = when (fissureType) {
            "裂缝", "裂隙" -> "ordinary"
            "钢铁裂缝", "钢铁裂隙" -> "hard"
            "九重天" -> "storm"
            else -> "ordinary"
        }

        val imgData = WebImgUtil.ImgData(
            url = "http://${webImgUtil.frontendAddress}/fissureList?type=$urlSuffix",
            imgName = "fissureList-${UUID.randomUUID()}",
            element = "#app",
            waitElement = ".warframeFissure"
        )
        missionQueryService.getFissures()
        val url = webImgUtil.getImgUrl(imgData)
        context.sender.sendImage(url)
        webImgUtil.deleteImg(imgData)
    }

    /** 准备当前每日突击缓存并发送突击图片；词库未就绪时发送初始化提示。 */
    @SystemLog(businessName = "获取日突击信息")
    @AParameter
    @Executor(action = "突击")
    suspend fun getSortie(context: ExecutionContext) {
        if (!translations.ready) {
            context.sender.sendText(PublicExportService.UNAVAILABLE_MESSAGE)
            return
        }
        if (!redisService.hasKey(translations.cacheKey(WF_SORTIE_KEY))) {
            val data = worldStateSource.get()
            missionQueryService.refreshSortie(data["Sorties"])
        }
        val imgData = WebImgUtil.ImgData(
            url = "http://${webImgUtil.frontendAddress}/sortie",
            imgName = "sortie-${UUID.randomUUID()}",
            element = "#app",
            waitElement = ".warframeSortie"
        )

        val url = webImgUtil.getImgUrl(imgData)
        context.sender.sendImage(url)
        webImgUtil.deleteImg(imgData)
    }

    /** 准备当前执刑官猎杀缓存并发送图片；词库未就绪时发送初始化提示。 */
    @SystemLog(businessName = "获取执刑官信息")
    @AParameter
    @Executor(action = "执(?:行|刑)官")
    suspend fun getArchonHunt(context: ExecutionContext) {
        if (!translations.ready) {
            context.sender.sendText(PublicExportService.UNAVAILABLE_MESSAGE)
            return
        }
        if (!redisService.hasKey(translations.cacheKey(WF_ARCHONHUNT_KEY))) {
            val data = worldStateSource.get()
            missionQueryService.refreshArchonHunt(data["LiteSorties"])
        }

        val imgData = WebImgUtil.ImgData(
            url = "http://${webImgUtil.frontendAddress}/archonHunt",
            imgName = "archonHuntInfo-${UUID.randomUUID()}",
            element = "#app",
            waitElement = ".warframeArchonHunt"
        )

        val url = webImgUtil.getImgUrl(imgData)
        context.sender.sendImage(url)
        webImgUtil.deleteImg(imgData)
    }

    /** 准备电波挑战缓存并发送电波图片；词库未就绪时发送初始化提示。 */
    @SystemLog(businessName = "获取午夜电波信息")
    @AParameter
    @Executor(action = "\\b(电波|午夜电波)\\b")
    suspend fun getNightWave(context: ExecutionContext) {
        if (!translations.ready) {
            context.sender.sendText(PublicExportService.UNAVAILABLE_MESSAGE)
            return
        }
        if (!redisService.hasKey(translations.cacheKey(WF_NIGHTWAVE_KEY))) {
            val data = worldStateSource.get()
            missionQueryService.refreshNightwave(data["SeasonInfo"])
        }

        val imgData = WebImgUtil.ImgData(
            url = "http://${webImgUtil.frontendAddress}/nightWave",
            imgName = "nightWave-${UUID.randomUUID()}",
            element = "#app",
            waitElement = ".warframeNightWave"
        )

        val url = webImgUtil.getImgUrl(imgData)
        context.sender.sendImage(url)
        webImgUtil.deleteImg(imgData)
    }

    /** 准备入侵列表缓存并发送入侵图片；词库未就绪时发送初始化提示。 */
    @SystemLog(businessName = "获取入侵列表")
    @AParameter
    @Executor(action = "\\b入侵\\b")
    suspend fun invasions(context: ExecutionContext) {
        if (!translations.ready) {
            context.sender.sendText(PublicExportService.UNAVAILABLE_MESSAGE)
            return
        }
        if (!redisService.hasKey(translations.cacheKey(WF_INVASIONS_KEY))) {
            val data = worldStateSource.get()
            missionQueryService.getInvasions(data["Invasions"])
        }
        val imgData = WebImgUtil.ImgData(
            url = "http://${webImgUtil.frontendAddress}/invasions",
            imgName = "invasions-${UUID.randomUUID()}",
            element = "#app",
            waitElement = ".warframeInvasions"
        )

        val url = webImgUtil.getImgUrl(imgData)
        context.sender.sendImage(url)
        webImgUtil.deleteImg(imgData)
        System.gc()
    }
}