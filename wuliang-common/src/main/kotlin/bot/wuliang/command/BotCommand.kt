package bot.wuliang.command

import bot.wuliang.botUtil.BotUtils

interface BotCommand {
    suspend fun execute(context: BotUtils.Context): String
}