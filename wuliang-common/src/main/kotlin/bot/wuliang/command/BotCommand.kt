package bot.wuliang.command

import bot.wuliang.botUtil.BotUtils
import java.util.regex.Matcher

interface BotCommand {
    suspend fun execute(context: BotUtils.Context): String
    fun execute(context: BotUtils.Context, matcher: Matcher): String
}