package bot.demo.txbot.common.logAop.database

import bot.demo.txbot.common.logAop.LogEntity

interface LogService {
    fun insertLog(logParam: LogEntity)
}