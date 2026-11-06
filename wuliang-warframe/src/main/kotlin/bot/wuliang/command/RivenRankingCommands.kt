package bot.wuliang.command

import bot.wuliang.adapter.context.ExecutionContext
import bot.wuliang.distribute.annotation.AParameter
import bot.wuliang.distribute.annotation.ActionService
import bot.wuliang.distribute.annotation.Executor
import bot.wuliang.imageProcess.WebImgUtil
import bot.wuliang.otherUtil.OtherUtil.STConversion.toMd5
import bot.wuliang.logAop.SystemLog
import bot.wuliang.riven.RivenRankingQuery
import bot.wuliang.riven.RivenRankingService
import bot.wuliang.translation.PublicExportService
import org.springframework.stereotype.Component
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import java.util.regex.Matcher

/** 紫卡周榜指令，参数解析与榜单同步由专用模块负责。 */
@Component
@ActionService
class RivenRankingCommands(
    private val rivenRankingService: RivenRankingService,
    private val rivenRankingQuery: RivenRankingQuery,
    private val translations: PublicExportService,
    private val webImgUtil: WebImgUtil,
) {

    /** 解析周榜筛选条件，缺少数据时同步官方周榜，然后发送筛选后的排行榜图片。 */
    @SystemLog(businessName = "获取DE紫卡数据")
    @AParameter
    @Executor(action = "\\b(紫卡价格|紫卡排行|紫卡|紫卡均价)(\\s+.*)?$")
    suspend fun getRivenRanking(context: ExecutionContext, matcher: Matcher) {
        if (!translations.ready) {
            context.sender.sendText(PublicExportService.UNAVAILABLE_MESSAGE)
            return
        }
        rivenRankingService.refreshRanking()

        val params = matcher.group(2)?.trim() ?: ""
        val urlParams = rivenRankingQuery.toQueryString(params)

        val currentMonday = LocalDate.now()
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .toString()

        val imgData = WebImgUtil.ImgData(
            url = "http://${webImgUtil.frontendAddress}/allRivenPrice$urlParams",
            imgName = "allRivenPrice-${currentMonday}-${rivenRankingService.cacheTag()}-${urlParams.toMd5()}-${translations.cacheTag()}",
            element = "#app",
            waitElement = ".warframeRivenAllPrice"
        )
        val url = webImgUtil.getImgUrl(imgData)
        context.sender.sendImage(url)
    }
}