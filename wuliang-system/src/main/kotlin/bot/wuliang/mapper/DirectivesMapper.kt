package bot.wuliang.mapper

import com.baomidou.mybatisplus.core.mapper.BaseMapper
import bot.wuliang.entity.DirectivesEntity
import org.apache.ibatis.annotations.Mapper

@Mapper
interface DirectivesMapper : BaseMapper<DirectivesEntity?> {
}