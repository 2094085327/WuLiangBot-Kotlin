package bot.wuliang.botLog.database.mapper

import bot.wuliang.botLog.database.entity.LogEntity
import com.baomidou.mybatisplus.core.mapper.BaseMapper
import com.baomidou.mybatisplus.core.metadata.IPage
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import java.time.LocalDateTime


/**
 * @description: 日志Mapper
 * @author Nature Zero
 * @date 2024/12/23 19:04
 */
@Mapper
interface LogMapper : BaseMapper<LogEntity?> {
    fun selectLogByTime(@Param("timeRanges") timeRanges: MutableList<Map<String, LocalDateTime>>): List<LogEntity>

    fun selectLogList(page: IPage<LogEntity>, logEntity: LogEntity): IPage<LogEntity>
}