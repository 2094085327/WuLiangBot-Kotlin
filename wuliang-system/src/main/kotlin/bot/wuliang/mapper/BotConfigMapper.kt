package bot.wuliang.mapper

import bot.wuliang.entity.BotConfigEntity
import com.baomidou.mybatisplus.core.mapper.BaseMapper
import org.apache.ibatis.annotations.Mapper

@Mapper
interface BotConfigMapper : BaseMapper<BotConfigEntity?> {
}