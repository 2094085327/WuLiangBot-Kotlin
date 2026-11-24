package bot.wuliang.adapter.context

import bot.wuliang.adapter.message.MessageSender
import bot.wuliang.message.BotMessage

/**
 * 命令执行上下文
 * 包含命令执行所需的所有上下文信息
 */
interface ExecutionContext {
    /**
     * 消息发送器
     */
    val sender: MessageSender

    /**
     * 请求上下文信息
     */
    val requestContext: RequestContext

    /**
     * 消息列表
     */
    val messages: List<BotMessage>

    /** 用户引用的原消息，独立于本条指令和直接发送的附件。 */
    val quotedMessage: QuotedMessage? get() = null
}

class QuotedMessage(val messages: List<BotMessage>)

/** 仅在图片指令匹配后使用。优先取本条附图，缺图时再取引用图。 */
fun ExecutionContext.directOrQuotedImages(): List<BotMessage.Image> =
    messages.filterIsInstance<BotMessage.Image>().ifEmpty {
        quotedMessage?.messages.orEmpty().filterIsInstance<BotMessage.Image>()
    }