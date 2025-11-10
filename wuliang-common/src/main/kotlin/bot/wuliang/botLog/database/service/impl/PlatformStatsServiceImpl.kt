package bot.wuliang.botLog.database.service.impl

import bot.wuliang.botLog.database.entity.PlatformStats
import bot.wuliang.botLog.database.mapper.PlatformStatsMapper
import bot.wuliang.botLog.database.service.PlatformStatsService
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
class PlatformStatsServiceImpl : ServiceImpl<PlatformStatsMapper?, PlatformStats?>(), PlatformStatsService  {
    @Autowired
    private lateinit var platformStatsMapper: PlatformStatsMapper

    override fun insertOrUpdatePlatformStats(platformStats: PlatformStats) {
        platformStatsMapper.insertOrUpdatePlatformStats(platformStats)
    }

    override fun getPlatformStatsWithOffset(offset: Int): List<PlatformStats?> {
        return platformStatsMapper.selectPlatformStatsWithOffset(offset)
    }

    override fun getAllMsgCount(): Int {
        return platformStatsMapper.selectAllMsgCount()
    }
}