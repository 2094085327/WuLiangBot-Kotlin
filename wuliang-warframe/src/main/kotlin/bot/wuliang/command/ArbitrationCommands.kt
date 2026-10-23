package bot.wuliang.command

import bot.wuliang.adapter.context.ExecutionContext
import bot.wuliang.arbitration.ArbitrationCommandQuery
import bot.wuliang.arbitration.ArbitrationImageService
import bot.wuliang.arbitration.ArbitrationUnavailableException
import bot.wuliang.botLog.logUtil.LoggerUtils.logError
import bot.wuliang.distribute.annotation.AParameter
import bot.wuliang.distribute.annotation.ActionService
import bot.wuliang.distribute.annotation.Executor
import bot.wuliang.logAop.SystemLog
import bot.wuliang.translation.PublicExportService
import kotlinx.coroutines.CancellationException
import org.springframework.stereotype.Component
import java.util.regex.Matcher

@Component
@ActionService
class ArbitrationCommands(private val images: ArbitrationImageService, private val translations: PublicExportService) {
    @SystemLog(businessName = "获取仲裁信息")
    @AParameter
    @Executor(action = ArbitrationCommandQuery.ACTION)
    suspend fun arbitration(context: ExecutionContext, matcher: Matcher) {
        val arguments = matcher.group(2).trim()
        if (arguments in setOf("帮助", "help", "?")) {
            context.sender.sendText(ArbitrationCommandQuery.HELP)
            return
        }
        if (!translations.ready) {
            context.sender.sendText(PublicExportService.UNAVAILABLE_MESSAGE)
            return
        }
        val url = try {
            images.imageUrl(ArbitrationCommandQuery.parse(matcher.group(1) != null, arguments))
        } catch (error: CancellationException) {
            throw error
        } catch (error: IllegalArgumentException) {
            context.sender.sendText(error.message.orEmpty())
            return
        } catch (error: ArbitrationUnavailableException) {
            context.sender.sendText(error.message.orEmpty())
            return
        } catch (error: Exception) {
            logError("生成仲裁图片失败", error)
            context.sender.sendText("仲裁图片暂时无法生成，请稍后重试。")
            return
        }
        context.sender.sendImage(url)
    }
}