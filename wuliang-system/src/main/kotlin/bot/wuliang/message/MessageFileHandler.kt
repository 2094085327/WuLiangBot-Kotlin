package bot.wuliang.message

import bot.wuliang.adapter.context.ExecutionContext

interface MessageFileHandler {
    val commandText: String
    suspend fun handleFile(context: ExecutionContext): Boolean
}