package bot.wuliang.command

import bot.wuliang.adapter.context.ExecutionContext
import bot.wuliang.botLog.logUtil.LoggerUtils.logError
import bot.wuliang.distribute.annotation.AParameter
import bot.wuliang.distribute.annotation.ActionService
import bot.wuliang.distribute.annotation.Executor
import bot.wuliang.logAop.SystemLog
import bot.wuliang.market.presentation.MarketRankingCommandQuery
import bot.wuliang.market.presentation.MarketRankingImageService
import bot.wuliang.market.presentation.MarketRankingUnavailableException
import kotlinx.coroutines.CancellationException
import org.springframework.stereotype.Component
import java.util.regex.Matcher

/** 每日成交排行指令；沿用统一截图上传和跨平台图片消息发送链路。 */
@Component
@ActionService
class MarketRankingCommands(private val images: MarketRankingImageService) {
    @SystemLog(businessName = "获取每日成交排行榜")
    @AParameter
    @Executor(action = MarketRankingCommandQuery.ACTION)
    suspend fun ranking(context: ExecutionContext, matcher: Matcher) {
        val arguments = matcher.group(1).trim()
        if (arguments.equals("帮助", true) || arguments.equals("help", true) || arguments == "?") {
            context.sender.sendText(MarketRankingCommandQuery.HELP)
            return
        }
        val request = try {
            MarketRankingCommandQuery.parse(arguments)
        } catch (error: IllegalArgumentException) {
            context.sender.sendText(error.message.orEmpty())
            return
        }
        val url = try {
            images.imageUrl(request)
        } catch (error: CancellationException) {
            throw error
        } catch (error: MarketRankingUnavailableException) {
            context.sender.sendText(error.message.orEmpty())
            return
        } catch (error: Exception) {
            logError("生成每日成交排行榜图片失败", error)
            context.sender.sendText("排行榜图片暂时无法生成，请稍后再试。")
            return
        }
        context.sender.sendImage(url)
    }
}