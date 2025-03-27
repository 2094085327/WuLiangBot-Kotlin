package bot.wuliang.mapper

import bot.wuliang.entity.WfRivenEntity
import com.baomidou.mybatisplus.core.mapper.BaseMapper
import org.apache.ibatis.annotations.Mapper

@Mapper
interface WfRivenMapper : BaseMapper<WfRivenEntity?> {
    fun insertOrUpdateBatch(list: List<WfRivenEntity>)

    fun selectAllRiven(): List<WfRivenEntity>
}