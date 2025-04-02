package bot.wuliang.service.impl

import bot.wuliang.entity.LifeRestartEntity
import bot.wuliang.mapper.LifeRestartMapper
import bot.wuliang.service.LifeRestartService
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service


/**
 * @description: 人生重开服务实现
 * @author Nature Zero
 * @date 2024/3/28 10:51
 */
@Service
class LifeRestartServiceImpl : ServiceImpl<LifeRestartMapper?, LifeRestartEntity?>(),
    LifeRestartService {
    @Autowired
    lateinit var restartMapper: LifeRestartMapper

    override fun insertTimesByRealId(realId: String) {

        val queryWrapper = QueryWrapper<LifeRestartEntity>().eq("real_id", realId)
        val existRestartInfo = restartMapper.selectOne(queryWrapper)
        val restartInfo = LifeRestartEntity(realId = realId, times = 1)
        if (existRestartInfo == null) restartMapper.insert(restartInfo)
        else {
            val restartInfoUpdate =
                LifeRestartEntity(realId = realId, times = existRestartInfo.times?.plus(1))
            restartMapper.update(restartInfoUpdate, queryWrapper)
        }
    }

    override fun selectRestartInfoByRealId(realId: String): LifeRestartEntity? {
        val queryWrapper = QueryWrapper<LifeRestartEntity>().eq("real_id", realId)
        return restartMapper.selectOne(queryWrapper)
    }
}