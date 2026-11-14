package bot.wuliang.command

import bot.wuliang.adapter.context.ExecutionContext
import bot.wuliang.botLog.logUtil.LoggerUtils.logError
import bot.wuliang.distribute.annotation.AParameter
import bot.wuliang.distribute.annotation.ActionService
import bot.wuliang.distribute.annotation.Executor
import bot.wuliang.logAop.SystemLog
import bot.wuliang.riven.analysis.RivenAnalysisException
import bot.wuliang.riven.range.RivenRangeImageService
import bot.wuliang.riven.range.RivenRangeQuery
import bot.wuliang.riven.range.RivenRangeService
import bot.wuliang.translation.PublicExportService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Component
import java.util.regex.Matcher

@Component
@ActionService
class RivenRangeCommands(
    private val translations: PublicExportService,
    private val ranges: RivenRangeService,
    private val images: RivenRangeImageService,
) {
    @SystemLog(businessName = "查询词条范围")
    @AParameter
    @Executor(action = "^(?:紫卡)?词条范围(?:\\s+([\\s\\S]*))?$")
    suspend fun range(context: ExecutionContext, matcher: Matcher) {
        val input = matcher.group(1).orEmpty().trim()
        if (input.isBlank() || input in setOf("帮助", "help", "?", "？")) {
            context.sender.sendText(RivenRangeQuery.HELP)
            return
        }
        if (!translations.ready) {
            context.sender.sendText(PublicExportService.UNAVAILABLE_MESSAGE)
            return
        }
        try {
            val query = RivenRangeQuery.parse(input)
            val type = withContext(Dispatchers.IO) { ranges.resolveType(query.target) }
            context.sender.sendImage(images.imageUrl(type, query.combination))
        } catch (error: CancellationException) {
            throw error
        } catch (error: RivenAnalysisException) {
            context.sender.sendText(error.message.orEmpty())
        } catch (error: Exception) {
            logError("词条范围查询失败", error)
            context.sender.sendText("词条范围暂时无法查询，请稍后重试。")
        }
    }
}