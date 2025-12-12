package bot.wuliang.service.impl

import bot.wuliang.entity.WfMissionTypeEntity
import bot.wuliang.mapper.WfMissionTypeMapper
import bot.wuliang.service.WfMissionTypeService
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service


/**
 * @description: WfMissionType的实现类
 * @author Nature Zero
 * @date 2024/11/24 00:08
 */
@Service
class WfMissionTypeServiceImpl @Autowired constructor(private val missionTypeMapper: WfMissionTypeMapper) :
    ServiceImpl<WfMissionTypeMapper?, WfMissionTypeEntity?>(), WfMissionTypeService {
    override fun getMissionType(currentTitle: String): WfMissionTypeEntity? {
        val missionQueryWrapper = QueryWrapper<WfMissionTypeEntity>().eq("mission_type", currentTitle)
        return missionTypeMapper.selectOne(missionQueryWrapper)
    }

    override fun getAllMissionType(): MutableList<WfMissionTypeEntity?> {
        return missionTypeMapper.selectList(null)
    }
}