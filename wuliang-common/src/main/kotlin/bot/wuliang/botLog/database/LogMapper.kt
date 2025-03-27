package bot.wuliang.botLog.database

import bot.wuliang.botLog.logAop.LogEntity
import com.baomidou.mybatisplus.core.mapper.BaseMapper
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
}