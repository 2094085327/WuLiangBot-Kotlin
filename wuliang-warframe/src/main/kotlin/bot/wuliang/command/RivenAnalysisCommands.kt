package bot.wuliang.command

import bot.wuliang.adapter.command.CommandRegistry
import bot.wuliang.adapter.context.ExecutionContext
import bot.wuliang.aipOcr.AipOcrClient
import bot.wuliang.aipOcr.OcrUnavailableException
import bot.wuliang.botLog.logUtil.LoggerUtils.logError
import bot.wuliang.distribute.annotation.AParameter
import bot.wuliang.distribute.annotation.ActionService
import bot.wuliang.distribute.annotation.Executor
import bot.wuliang.logAop.SystemLog
import bot.wuliang.message.BotMessage
import bot.wuliang.message.MessageContinuation
import bot.wuliang.riven.analysis.*
import bot.wuliang.riven.samples.RivenMarketReference
import bot.wuliang.riven.samples.RivenSampleService
import bot.wuliang.translation.PublicExportService
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.springframework.stereotype.Component
import java.util.regex.Matcher
import javax.annotation.PreDestroy
import kotlin.time.Duration.Companion.milliseconds

@Component
@ActionService
class RivenAnalysisCommands(
    private val translations: PublicExportService,
    private val sessions: RivenAnalysisSessions,
    private val ocr: AipOcrClient,
    private val parser: RivenOcrParser,
    private val textParser: RivenTextParser,
    private val analysis: RivenValueAnalysis,
    private val images: RivenAnalysisImageService,
    private val samples: RivenSampleService,
) : MessageContinuation {
    override val commandText = "紫卡分析"
    private val recognizing = Semaphore(2)
    private val waitingScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    @SystemLog(businessName = "紫卡文字分析")
    @AParameter
    @Executor(action = "^紫卡分析\\s+-文字(?:\\s+([\\s\\S]*))?$")
    suspend fun analyzeText(context: ExecutionContext, matcher: Matcher) {
        if (!translations.ready) {
            context.sender.sendText(PublicExportService.UNAVAILABLE_MESSAGE)
            return
        }
        if (context.messages.any { it is BotMessage.Image }) {
            context.sender.sendText("文字模式请只发送武器和词条；图片请使用「紫卡分析」指令。")
            return
        }
        val riven = try {
            textParser.parse(matcher.group(1).orEmpty())
        } catch (error: RivenAnalysisException) {
            context.sender.sendText(error.message.orEmpty())
            return
        }
        sessions.cancel(context.requestContext)
        processReport(context, "正在分析紫卡词条…", "请检查后重新发送完整的文字分析指令。") {
            analysis.analyzeText(riven)
        }
    }

    @SystemLog(businessName = "紫卡数值分析")
    @AParameter
    @Executor(action = "^紫卡分析(?:\\s+(?:等级\\s*)?([0-8]))?$")
    suspend fun analyze(context: ExecutionContext, matcher: Matcher) {
        if (!translations.ready) {
            context.sender.sendText(PublicExportService.UNAVAILABLE_MESSAGE)
            return
        }
        val pictures = context.messages.filterIsInstance<BotMessage.Image>()
        if (pictures.size > 1) {
            context.sender.sendText("每次只接受一张紫卡图片，请截取单张紫卡后重新发送。")
            return
        }
        if (sessions.isBusy(context.requestContext)) {
            context.sender.sendText("上一张紫卡正在分析，请稍候。")
            return
        }
        val rank = matcher.group(1)?.toInt()
        if (pictures.isEmpty()) {
            val pending = sessions.begin(context.requestContext, rank)
            remindIfImageMissing(context, pending)
            context.sender.sendText("1分钟内发送一张紫卡图片开始分析。\n也可以使用「紫卡分析 -文字 执法者/+195.9% 暴击几率/+167.6% 近战伤害」来进行文字分析")
        } else {
            sessions.cancel(context.requestContext)
            process(context, pictures.single(), rank)
        }
    }

    private fun remindIfImageMissing(context: ExecutionContext, pending: PendingRivenImage) {
        val requestContext = context.requestContext
        val sender = context.sender
        waitingScope.launch {
            try {
                delay((pending.expiresAtMillis - System.currentTimeMillis()).coerceAtLeast(0).milliseconds)
                if (sessions.expire(requestContext, pending)) {
                    sender.sendText("未接收到紫卡图片，请重新发送「紫卡分析」和图片进行分析。")
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                logError("紫卡图片等待超时提醒失败", error)
            }
        }
    }

    @PreDestroy
    fun close() {
        waitingScope.cancel()
    }

    override suspend fun handleIfPending(context: ExecutionContext): Boolean {
        val pictures = context.messages.filterIsInstance<BotMessage.Image>()
        if (pictures.isEmpty()) return false
        val text = context.messages.filterIsInstance<BotMessage.Text>().joinToString(" ") { it.content }.trim()
            .removePrefix("/")
        // 附带其他有效指令的图片仍进入原有命令链；普通图片说明文字可以保留。
        if (text.isNotEmpty() && CommandRegistry.getCommandWithMatcher(text) != null) return false
        val pending = sessions.pending(context.requestContext) ?: return false
        if (pictures.size != 1) {
            context.sender.sendText("每次只接受一张紫卡图片，请在本次等待时间内重新发送一张。")
            return true
        }
        if (!sessions.consume(context.requestContext, pending)) return true
        process(context, pictures.single(), pending.rank)
        return true
    }

    private suspend fun process(context: ExecutionContext, image: BotMessage.Image, rank: Int?) =
        processReport(context, "已收到图片，正在识别并分析紫卡数值…", "请重新发送「紫卡分析」和图片进行分析。") {
            analysis.analyzeOcr(parser.parse(ocr.getImageText(image.url, image.bytes), rank))
        }

    private suspend fun processReport(
        context: ExecutionContext, progressMessage: String, retryMessage: String,
        buildReport: () -> RivenAnalysisReport
    ) {
        val token = sessions.acquire(context.requestContext)
        if (token == null) {
            context.sender.sendText("上一张紫卡正在分析，请稍候。")
            return
        }
        try {
            if (!translations.ready) {
                context.sender.sendText(PublicExportService.UNAVAILABLE_MESSAGE)
                return
            }
            context.sender.sendText(progressMessage)
            withTimeout(120_000.milliseconds) {
                val report = recognizing.withPermit {
                    withContext(Dispatchers.IO) {
                        buildReport()
                    }
                }
                val reference = withContext(Dispatchers.IO) {
                    try {
                        samples.marketReference(report)
                    } catch (error: Exception) {
                        if (error is InterruptedException) {
                            Thread.currentThread().interrupt(); throw error
                        }
                        logError("紫卡市场参考暂不可用", error)
                        RivenMarketReference("UNAVAILABLE")
                    }
                }
                context.sender.sendImage(images.imageUrl(report.copy(marketReference = reference)))
            }
        } catch (_: TimeoutCancellationException) {
            context.sender.sendText("紫卡分析等待超时，请稍后重试。")
        } catch (error: CancellationException) {
            throw error
        } catch (error: RivenAnalysisException) {
            context.sender.sendText(error.message?.takeIf(String::isNotBlank) ?: retryMessage)
        } catch (error: OcrUnavailableException) {
            context.sender.sendText(error.message?.takeIf(String::isNotBlank) ?: retryMessage)
        } catch (error: Exception) {
            logError("紫卡数值分析失败", error)
            context.sender.sendText("紫卡分析暂时失败，请稍后重新发送「紫卡分析」和图片。")
        } finally {
            sessions.release(context.requestContext, token)
        }
    }
}