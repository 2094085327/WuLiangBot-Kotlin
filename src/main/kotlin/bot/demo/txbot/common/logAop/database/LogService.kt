package bot.demo.txbot.common.logAop.database

import bot.demo.txbot.common.logAop.LogEntity
import com.baomidou.mybatisplus.core.metadata.IPage

interface LogService {
    fun insertLog(logParam: LogEntity)

    fun getLog(page:IPage<LogEntity?>): IPage<LogEntity?>
}