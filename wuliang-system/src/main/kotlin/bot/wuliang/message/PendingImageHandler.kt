package bot.wuliang.message

import bot.wuliang.adapter.command.CommandRegistry
import bot.wuliang.adapter.context.ExecutionContext
import bot.wuliang.adapter.context.RequestContext

/** 共用后续收图流程；数量超限时保留等待，消费成功后才执行业务处理。 */
suspend fun <Pending : Any> handlePendingImages(
    context: ExecutionContext,
    maxImages: Int,
    tooManyImagesMessage: String,
    pending: (RequestContext) -> Pending?,
    consume: (RequestContext, Pending) -> Boolean,
    process: suspend (List<BotMessage.Image>, Pending) -> Unit,
): Boolean {
    val pictures = context.messages.filterIsInstance<BotMessage.Image>()
    if (pictures.isEmpty()) return false
    val text = context.messages.filterIsInstance<BotMessage.Text>().joinToString(" ") { it.content }.trim()
        .removePrefix("/")
    // 附带其他有效指令的图片仍进入原有命令链；普通图片说明文字可以保留。
    if (text.isNotEmpty() && CommandRegistry.getCommandWithMatcher(text) != null) return false
    val requestContext = context.requestContext
    val session = pending(requestContext) ?: return false
    if (pictures.size > maxImages) {
        context.sender.sendText(tooManyImagesMessage)
        return true
    }
    if (!consume(requestContext, session)) return true
    process(pictures, session)
    return true
}