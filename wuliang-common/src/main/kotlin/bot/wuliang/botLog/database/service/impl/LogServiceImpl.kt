package bot.wuliang.botLog.database.service.impl

import bot.wuliang.botLog.database.mapper.LogMapper
import bot.wuliang.botLog.database.service.LogService
import bot.wuliang.botLog.logAop.LogEntity
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper
import com.baomidou.mybatisplus.core.metadata.IPage
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.time.LocalDateTime


/**
 * @description: 日志实现类
 * @author Nature Zero
 * @date 2024/12/23 21:18
 */
@Service
class LogServiceImpl : ServiceImpl<LogMapper?, LogEntity?>(), LogService {
    @Autowired
    private lateinit var logMapper: LogMapper
    override fun insertLog(logParam: LogEntity) {
        logMapper.insert(logParam)
    }

    override fun getLog(page: IPage<LogEntity?>): IPage<LogEntity?> {
        val queryWrapper = QueryWrapper<LogEntity>()
        queryWrapper.orderByDesc("create_time")
        return logMapper.selectPage(page, queryWrapper)
    }

    override fun selectLogByTime(timeRanges: MutableList<Map<String, LocalDateTime>>): List<LogEntity> {
        return logMapper.selectLogByTime(timeRanges)
    }

    override fun selectList(page:IPage<LogEntity>,logEntity: LogEntity): IPage<LogEntity> {
        return logMapper.selectLogList(page,logEntity)
    }
}