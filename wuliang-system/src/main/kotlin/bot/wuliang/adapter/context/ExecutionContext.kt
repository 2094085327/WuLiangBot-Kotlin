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
}
