package bot.wuliang.service

import bot.wuliang.entity.WfMissionTypeEntity
import com.baomidou.mybatisplus.extension.service.IService

/**
 * @author Nature Zero
 * @description 针对表【wf_mission_type】的数据库操作Service
 * @createDate 2024-11-24 00:01:01
 */
interface WfMissionTypeService : IService<WfMissionTypeEntity?> {
    fun getMissionType(currentTitle: String): WfMissionTypeEntity?

    fun getAllMissionType(): MutableList<WfMissionTypeEntity?>
}
