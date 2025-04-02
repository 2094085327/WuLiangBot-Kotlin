package mapper

import com.baomidou.mybatisplus.core.mapper.BaseMapper
import entity.DirectivesEntity
import org.apache.ibatis.annotations.Mapper

@Mapper
interface DirectivesMapper : BaseMapper<DirectivesEntity?> {
}