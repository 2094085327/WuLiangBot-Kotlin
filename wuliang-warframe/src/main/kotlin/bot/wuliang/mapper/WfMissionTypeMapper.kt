package bot.wuliang.mapper

import bot.wuliang.entity.WfMissionTypeEntity
import com.baomidou.mybatisplus.core.mapper.BaseMapper
import org.apache.ibatis.annotations.Mapper

/**
 * @author Nature Zero
 * @description 针对表【wf_mission_type】的数据库操作Mapper
 * @createDate 2024-11-24 00:01:01
 * @Entity bot.demo.txbot.warframe.WfMissionTypeEntity
 */
@Mapper
interface WfMissionTypeMapper : BaseMapper<WfMissionTypeEntity?>
