package bot.wuliang.command

import bot.wuliang.adapter.context.ExecutionContext
import bot.wuliang.config.WARFRAME_AMP_PNG
import bot.wuliang.config.WARFRAME_CETUS_WISP_PNG
import bot.wuliang.distribute.annotation.AParameter
import bot.wuliang.distribute.annotation.ActionService
import bot.wuliang.distribute.annotation.Executor
import bot.wuliang.httpUtil.HttpUtil.urlEncode
import bot.wuliang.imageProcess.WebImgUtil
import bot.wuliang.logAop.SystemLog
import bot.wuliang.respEnum.WarframeRespEnum
import bot.wuliang.translation.PublicExportService
import org.springframework.stereotype.Component
import java.util.regex.Matcher

/** Wiki 链接、参考图片与物品翻译指令，不参与市场查询。 */
@Component
@ActionService
class WarframeReferenceCommands(
    private val translations: PublicExportService,
    private val webImgUtil: WebImgUtil,
) {

    /** 对指令中的 Wiki 条目名称做 URL 编码，向当前会话发送对应链接。 */
    @SystemLog(businessName = "获取物品Wiki链接")
    @AParameter
    @Executor(action = "wiki (.*)")
    suspend fun getWikiUrl(context: ExecutionContext, matcher: Matcher) {
        val key = matcher.group(1)
        val wikiUrl = "https://warframe.huijiwiki.com/wiki/${key.urlEncode()}"
        context.sender.sendText(WarframeRespEnum.SEARCH_WIKI.message + wikiUrl)
    }

    /** 读取本地增幅器参考图片并发送到当前会话。 */
    @SystemLog(businessName = "获取增幅器序号")
    @AParameter
    @Executor(action = "(?i)\\b(增幅器|指挥官|指挥官武器|amp)\\b")
    suspend fun getAmp(context: ExecutionContext) {
        val imgData = WebImgUtil.ImgData(
            url = WARFRAME_AMP_PNG,
            imgName = "amp",
            local = true,
        )
        val url = webImgUtil.getImgUrl(imgData = imgData)
        context.sender.sendImage(url)
    }

    /** 读取本地希图斯幽魂参考图片并发送到当前会话。 */
    @SystemLog(businessName = "获取希图斯幽魂")
    @AParameter
    @Executor(action = "(?i)\\b(幽魂|希图斯幽魂)\\b")
    suspend fun getCetusWisp(context: ExecutionContext) {
        val imgData = WebImgUtil.ImgData(
            url = WARFRAME_CETUS_WISP_PNG,
            imgName = "cetus_wisp",
            local = true,
        )
        val url = webImgUtil.getImgUrl(imgData = imgData)
        context.sender.sendImage(url)
    }

    /** 查询物品名称的中英文对应关系，最多发送二十条结果，未命中时发送提示。 */
    @SystemLog(businessName = "获取物品对应翻译")
    @AParameter
    @Executor(action = "(?i)\\b翻译 (.*)\\b")
    suspend fun translation(context: ExecutionContext, matcher: Matcher) {
        if (!translations.ready) {
            context.sender.sendText(PublicExportService.UNAVAILABLE_MESSAGE)
            return
        }
        val inputText = matcher.group(1).trim()
        val results = translations.index().translateQuery(inputText)
        context.sender.sendText(
            results.take(20).joinToString("\n").ifEmpty { WarframeRespEnum.SEARCH_MATCH_NOT_FOUND.message })
    }
}