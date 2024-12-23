package bot.demo.txbot.common.logAop.database

import bot.demo.txbot.common.logAop.LogEntity
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service


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
}