package bot.wuliang.command

import bot.wuliang.adapter.context.ExecutionContext
import bot.wuliang.config.WfMarketConfig.WF_INCARNON_KEY
import bot.wuliang.config.WfMarketConfig.WF_SIMARIS_KEY
import bot.wuliang.distribute.annotation.AParameter
import bot.wuliang.distribute.annotation.ActionService
import bot.wuliang.distribute.annotation.Executor
import bot.wuliang.imageProcess.WebImgUtil
import bot.wuliang.logAop.SystemLog
import bot.wuliang.redis.RedisService
import bot.wuliang.respEnum.WarframeRespEnum
import bot.wuliang.translation.PublicExportService
import bot.wuliang.worldstate.service.LocalRotationService
import bot.wuliang.worldstate.service.MoodSpiralService
import bot.wuliang.worldstate.source.WorldStateSource
import org.springframework.stereotype.Component
import java.util.*

/** 钢路、回廊、双衍与结合仪式的补充数据指令。 */
@Component
@ActionService
class RotationCommands(
    private val moodSpiralService: MoodSpiralService,
    private val localRotationService: LocalRotationService,
    private val worldStateSource: WorldStateSource,
    private val translations: PublicExportService,
    private val webImgUtil: WebImgUtil,
    private val redisService: RedisService,
) {

    /** 读取钢铁之路奖励并发送对应图片，缺少词库时返回初始化提示。 */
    @SystemLog(businessName = "获取钢铁之路兑换信息")
    @AParameter
    @Executor(action = "钢铁")
    suspend fun getSteelPath(context: ExecutionContext) {
        if (!translations.ready) {
            context.sender.sendText(PublicExportService.UNAVAILABLE_MESSAGE)
            return
        }
        val imgData = WebImgUtil.ImgData(
            url = "http://${webImgUtil.frontendAddress}/steelPath",
            imgName = "steelPath-${UUID.randomUUID()}",
            element = "#app",
            waitElement = ".wfSteelPath"
        )

        val url = webImgUtil.getImgUrl(imgData)
        context.sender.sendImage(url)
        webImgUtil.deleteImg(imgData)
    }

    /** 准备本地回廊轮换缓存并发送回廊图片。 */
    @SystemLog(businessName = "获取本周灵化信息")
    @AParameter
    @Executor(action = "\\b(本周灵化|这周灵化|灵化|回廊|钢铁回廊|本周回廊)\\b")
    suspend fun incarnon(context: ExecutionContext) {
        if (!translations.ready) {
            context.sender.sendText(PublicExportService.UNAVAILABLE_MESSAGE)
            return
        }
        if (!redisService.hasKey(translations.cacheKey(WF_INCARNON_KEY))) {
            localRotationService.getIncarnon()
        }

        val imgData = WebImgUtil.ImgData(
            url = "http://${webImgUtil.frontendAddress}/incarnon",
            imgName = "incarnon-${UUID.randomUUID()}",
            element = "#app",
            waitElement = ".warframeIncarnon"
        )

        val url = webImgUtil.getImgUrl(imgData)
        context.sender.sendImage(url)
        webImgUtil.deleteImg(imgData)
        System.gc()
    }

    /** 准备详细双衍轮换缓存，存在有效轮换时发送对应图片。 */
    @SystemLog(businessName = "获取双衍平原状态信息")
    @AParameter
    @Executor(action = "\\b(双衍|双衍平原|双衍状态|双衍平原状态|回廊状态|虚空平原状态|复眠螺旋|复眠螺旋状态|王境状态)\\b")
    suspend fun moodSpirals(context: ExecutionContext) {
        if (!translations.ready) {
            context.sender.sendText(PublicExportService.UNAVAILABLE_MESSAGE)
            return
        }
        if (moodSpiralService.getMoodSpirals() == null) {
            context.sender.sendText(WarframeRespEnum.SPIRALS_ERROR.message)
            return
        }
        // 生成和发送图像
        val imgData = WebImgUtil.ImgData(
            url = "http://${webImgUtil.frontendAddress}/spirals",
            imgName = "spirals-${UUID.randomUUID()}",
            element = "#app",
            waitElement = ".warframeSpirals"
        )

        val url = webImgUtil.getImgUrl(imgData)
        context.sender.sendImage(url)
        webImgUtil.deleteImg(imgData)
        System.gc()
    }

    /** 准备今日结合仪式目标缓存，生成并发送目标及地点图片。 */
    @SystemLog(businessName = "获取圣殿结合仪式目标")
    @AParameter
    @Executor(action = "\\b(结合仪式|结合|结合目标|大黄脸)\\b")
    suspend fun sanctuarySynthesisTargets(context: ExecutionContext) {
        if (!translations.ready) {
            context.sender.sendText(PublicExportService.UNAVAILABLE_MESSAGE)
            return
        }
        if (!redisService.hasKey(translations.cacheKey(WF_SIMARIS_KEY))) {
            val data = worldStateSource.get()
            localRotationService.getSimaris(data["LibraryInfo"])
        }

        val imgData = WebImgUtil.ImgData(
            url = "http://${webImgUtil.frontendAddress}/simaris",
            imgName = "simaris-${UUID.randomUUID()}",
            element = "#app",
            waitElement = ".warframeSimaris"
        )

        val url = webImgUtil.getImgUrl(imgData)
        context.sender.sendImage(url)
        webImgUtil.deleteImg(imgData)
    }
}