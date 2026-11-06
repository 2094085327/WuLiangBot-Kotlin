package bot.wuliang.message

import bot.wuliang.adapter.context.ExecutionContext

/** 由已执行的命令开启的后续消息入口，仍受该命令的启用状态约束。 */
interface MessageContinuation {
    val commandText: String

    /** 只有成功接管本条消息时返回 true。 */
    suspend fun handleIfPending(context: ExecutionContext): Boolean
}