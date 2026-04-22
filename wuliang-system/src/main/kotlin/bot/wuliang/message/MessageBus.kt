package bot.wuliang.message

import bot.wuliang.adapter.context.ExecutionContext

/**
 * 消息总线接口
 */
interface MessageBus {
    /**
     * 分发消息
     * @param context 执行上下文
     */
    suspend fun dispatch(context: ExecutionContext)
}