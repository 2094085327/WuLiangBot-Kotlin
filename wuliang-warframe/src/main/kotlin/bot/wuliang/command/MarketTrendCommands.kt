package bot.wuliang.command

import bot.wuliang.adapter.context.ExecutionContext
import bot.wuliang.botLog.logUtil.LoggerUtils.logError
import bot.wuliang.distribute.annotation.AParameter
import bot.wuliang.distribute.annotation.ActionService
import bot.wuliang.distribute.annotation.Executor
import bot.wuliang.logAop.SystemLog
import bot.wuliang.market.presentation.MarketTrendCommandQuery
import bot.wuliang.market.presentation.MarketTrendImageService
import bot.wuliang.market.presentation.MarketTrendUnavailableException
import kotlinx.coroutines.CancellationException
import org.springframework.stereotype.Component
import java.util.regex.Matcher

@Component
@ActionService
class MarketTrendCommands(private val images: MarketTrendImageService) {
    @SystemLog(businessName = "获取WM物品成交走势")
    @AParameter
    @Executor(action = MarketTrendCommandQuery.ACTION)
    suspend fun trend(context: ExecutionContext, matcher: Matcher) {
        val arguments = matcher.group(1).trim()
        if (arguments.isEmpty() || arguments.lowercase() in setOf("帮助", "help", "?")) {
            context.sender.sendText(MarketTrendCommandQuery.HELP)
            return
        }
        val request = try {
            MarketTrendCommandQuery.parse(arguments)
        } catch (error: IllegalArgumentException) {
            context.sender.sendText(error.message.orEmpty())
            return
        }
        val url = try {
            images.imageUrl(request)
        } catch (error: CancellationException) {
            throw error
        } catch (error: MarketTrendUnavailableException) {
            context.sender.sendText(error.message.orEmpty())
            return
        } catch (error: Exception) {
            logError("生成WM成交走势图片失败", error)
            context.sender.sendText("走势图片暂时无法生成，请稍后再试。")
            return
        }
        context.sender.sendImage(url)
    }
}