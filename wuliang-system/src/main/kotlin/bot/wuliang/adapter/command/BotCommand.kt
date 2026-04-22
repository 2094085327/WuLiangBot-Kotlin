package bot.wuliang.adapter.command

import bot.wuliang.adapter.context.ExecutionContext
import java.util.regex.Matcher

/**
 * 命令接口
 */
interface BotCommand {
    /**
     * 执行命令
     * @param context 执行上下文
     * @param matcher 匹配结果
     */
    suspend fun execute(context: ExecutionContext, matcher: Matcher?): Any?
}