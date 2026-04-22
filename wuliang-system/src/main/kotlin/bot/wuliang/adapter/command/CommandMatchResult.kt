package bot.wuliang.adapter.command

import java.util.regex.Matcher

/**
 * 命令匹配结果
 */
data class CommandMatchResult(
    val command: BotCommand,
    val matcher: Matcher,
    val isRegex: Boolean,
    val matchedRegex: String?
)