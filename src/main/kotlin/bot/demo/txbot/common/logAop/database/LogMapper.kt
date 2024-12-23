package bot.demo.txbot.common.logAop.database

import bot.demo.txbot.common.logAop.LogEntity
import com.baomidou.mybatisplus.core.mapper.BaseMapper
import org.apache.ibatis.annotations.Mapper


/**
 * @description: 日志Mapper
 * @author Nature Zero
 * @date 2024/12/23 19:04
 */
@Mapper
interface LogMapper : BaseMapper<LogEntity?>