package bot.wuliang.command

import bot.wuliang.adapter.command.CommandRegistry
import bot.wuliang.adapter.context.ExecutionContext
import bot.wuliang.adapter.context.RequestContext
import bot.wuliang.arbitration.log.*
import bot.wuliang.arbitration.log.archive.ArbitrationLogUploader
import bot.wuliang.arbitration.log.history.ArbitrationLogHistoryQuery
import bot.wuliang.arbitration.log.history.ArbitrationLogHistoryService
import bot.wuliang.botLog.logUtil.LoggerUtils.logError
import bot.wuliang.botLog.logUtil.LoggerUtils.logInfo
import bot.wuliang.distribute.annotation.AParameter
import bot.wuliang.distribute.annotation.ActionService
import bot.wuliang.distribute.annotation.Executor
import bot.wuliang.message.BotMessage
import bot.wuliang.message.MessageFileHandler
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Component
import java.util.regex.Matcher

@Component
@ActionService
class ArbitrationLogCommands(
    private val service: ArbitrationLogService,
    private val images: ArbitrationLogImageService,
    private val history: ArbitrationLogHistoryService,
) : MessageFileHandler {
    override val commandText = "仲裁分析"

    private data class Identity(
        val platform: String,
        val bot: String,
        val type: String,
        val group: String?,
        val user: String
    )

    private data class Pending(val options: ArbitrationLogOptions, val expires: Long)

    private val waiting = mutableMapOf<Identity, Pending>()
    private val processing = Semaphore(1)

    @AParameter
    @Executor(action = ArbitrationLogHistoryQuery.ACTION)
    suspend fun history(context: ExecutionContext, matcher: Matcher) {
        val arguments = matcher.group(1).orEmpty().trim()
        if (arguments in setOf("帮助", "help", "?")) {
            context.sender.sendText(ArbitrationLogHistoryQuery.HELP)
            return
        }
        val page = try {
            ArbitrationLogHistoryQuery.page(arguments)
        } catch (error: IllegalArgumentException) {
            context.sender.sendText(error.message.orEmpty())
            return
        }
        if (!processing.tryAcquire()) {
            context.sender.sendText("正在处理另一条仲裁请求，请稍后重试。")
            return
        }
        try {
            context.sender.sendText("无量姬正在查询中，请稍等")
            val request = context.requestContext
            val uploader = ArbitrationLogUploader(request.platform, request.botId, request.messageType, request.userId)
            val url = withContext(Dispatchers.IO) { images.historyImageUrl(history.report(uploader, page)) }
            context.sender.sendImage(url)
        } catch (error: CancellationException) {
            throw error
        } catch (error: IllegalArgumentException) {
            context.sender.sendText(error.message.orEmpty())
        } catch (error: Exception) {
            logInfo("仲裁历史查询失败，异常类型：${error.javaClass.simpleName}")
            context.sender.sendText("仲裁记录查询或图片生成失败，请稍后重试。")
        } finally {
            processing.release()
        }
    }

    @AParameter
    @Executor(action = ArbitrationLogQuery.ACTION)
    suspend fun analyze(context: ExecutionContext, matcher: Matcher) {
        val files = context.messages.filterIsInstance<BotMessage.File>()
        warnGroupLogUpload(context, files)
        val arguments = matcher.group(1).orEmpty().trim()
        if (arguments in setOf("帮助", "help", "?")) {
            context.sender.sendText(ArbitrationLogQuery.HELP)
            return
        }
        val options = try {
            ArbitrationLogQuery.parse(arguments)
        } catch (error: IllegalArgumentException) {
            context.sender.sendText(error.message.orEmpty())
            return
        }
        if (files.isEmpty() && !options.previous && options.recordId == null) {
            val accepted = synchronized(waiting) {
                val now = System.currentTimeMillis()
                waiting.entries.removeIf { it.value.expires < now }
                if (waiting.size >= 128 && identity(context.requestContext) !in waiting) false else {
                    waiting[identity(context.requestContext)] =
                        Pending(options, now + ArbitrationLogLimits.UPLOAD_WAIT_MILLIS)
                    true
                }
            }
            context.sender.sendText(if (accepted) "请在${ArbitrationLogLimits.UPLOAD_WAIT_MINUTES}分钟内发送主机的 EE.log，最大${ArbitrationLogLimits.SIZE_LABEL}。\nEE.log文件位置：%LOCALAPPDATA%\\Warframe" else "当前等待人数较多，请稍后重试。")
            return
        }
        val pending = synchronized(waiting) { waiting[identity(context.requestContext)] }
        process(context, files, options, pending)
    }

    override suspend fun handleFile(context: ExecutionContext): Boolean {
        val text = context.messages.filterIsInstance<BotMessage.Text>().joinToString(" ") { it.content }.trim()
            .removePrefix("/")
        if (text.isNotEmpty() && CommandRegistry.getCommandWithMatcher(text) != null) return false
        val files = context.messages.filterIsInstance<BotMessage.File>()
        val pending = synchronized(waiting) { waiting[identity(context.requestContext)] }
        if (pending == null && files.none { it.fileName.equals("EE.log", true) }) return false
        warnGroupLogUpload(context, files)
        if (pending != null && pending.expires < System.currentTimeMillis()) {
            synchronized(waiting) { waiting.remove(identity(context.requestContext), pending) }
            context.sender.sendText("等待已超时，请重新发送「仲裁分析」和 EE.log。")
            return true
        }
        process(context, files, pending?.options ?: ArbitrationLogOptions(), pending)
        return true
    }

    private suspend fun warnGroupLogUpload(context: ExecutionContext, files: List<BotMessage.File>) {
        if (context.requestContext.groupId.isNullOrBlank() || files.none { it.fileName.equals("EE.log", true) }) return
        try {
            context.sender.sendText("安全提醒：EE.log 可能包含账号、设备或网络信息，在群聊中发送有泄露风险，建议尽快撤回，通过私聊发送日志。")
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            logError("群聊日志安全提醒发送失败，异常类型：${error.javaClass.simpleName}")
        }
    }

    private suspend fun process(
        context: ExecutionContext,
        files: List<BotMessage.File>,
        options: ArbitrationLogOptions,
        pending: Pending?
    ) {
        val stored = options.previous || options.recordId != null
        if (stored && files.isNotEmpty()) {
            context.sender.sendText("查询已保存的记录时无需附加文件。分析新文件时请移除「上次」或「#编号」。")
            return
        }
        if (!stored && (files.size != 1 || !files.single().fileName.equals("EE.log", true))) {
            context.sender.sendText("每次请发送一个原始 EE.log 文件。")
            return
        }
        if (!processing.tryAcquire()) {
            context.sender.sendText("正在处理另一条仲裁请求，请稍后重试。")
            return
        }
        try {
            if (pending != null && !synchronized(waiting) {
                    waiting.remove(
                        identity(context.requestContext),
                        pending
                    )
                }) {
                context.sender.sendText("分析设置已更新，请重新发送分析指令。")
                return
            }
            context.sender.sendText(if (stored) "无量姬正在查询中，请稍等" else "已收到 EE.log，正在分析并保存结构化记录…")
            val request = context.requestContext
            val uploader = ArbitrationLogUploader(request.platform, request.botId, request.messageType, request.userId)
            val url = withContext(Dispatchers.IO) {
                val report = when {
                    options.recordId != null -> service.record(options, uploader)
                    options.previous -> service.previous(options, uploader)
                    else -> service.analyze(files.single().url, options, uploader)
                }
                images.imageUrl(report)
            }
            context.sender.sendImage(url)
        } catch (error: CancellationException) {
            throw error
        } catch (error: IllegalArgumentException) {
            context.sender.sendText(error.message.orEmpty())
        } catch (error: Exception) {
            // 下载异常可能携带带签名的附件地址，日志不输出异常正文或原文件。
            logInfo("仲裁日志分析失败，异常类型：${error.javaClass.simpleName}")
            context.sender.sendText(
                if (options.recordId != null) "记录读取或图片生成失败，请稍后重试「仲裁分析 #${options.recordId}」。"
                else if (options.previous) "记录读取或图片生成失败，请稍后重试「仲裁分析 上次」。"
                else "日志下载或图片生成失败，请稍后重新发送「仲裁分析」和 EE.log。"
            )
        } finally {
            processing.release()
        }
    }

    private fun identity(context: RequestContext) =
        Identity(context.platform, context.botId, context.messageType, context.groupId, context.userId)
}