package bot.wuliang.botLog.database

import bot.wuliang.botLog.logAop.LogEntity
import com.baomidou.mybatisplus.core.metadata.IPage
import java.time.LocalDateTime

interface LogService {
    fun insertLog(logParam: LogEntity)

    fun getLog(page: IPage<LogEntity?>): IPage<LogEntity?>

    /**
     * 根据时间范围的数组获取数据
     *
     * @param timeRanges
     */
    fun selectLogByTime(timeRanges: MutableList<Map<String, LocalDateTime>>): List<LogEntity>
}