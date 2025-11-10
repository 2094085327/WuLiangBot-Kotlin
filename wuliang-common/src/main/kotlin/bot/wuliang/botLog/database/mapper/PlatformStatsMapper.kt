package bot.wuliang.botLog.database.mapper

import bot.wuliang.botLog.database.entity.PlatformStats
import com.baomidou.mybatisplus.core.mapper.BaseMapper
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param

/**
 * @description: 消息量统计
 * @author Nature Zero
 * @date 2025/11/06 09:33
 */
@Mapper
interface PlatformStatsMapper : BaseMapper<PlatformStats?> {
    /**
     * 插入或更新平台统计数据
     *
     * @param platformStats 平台统计数据实体
     */
    fun insertOrUpdatePlatformStats(platformStats: PlatformStats)

    /**
     * 获取对应时间范围的平台统计数据
     *
     * @param offset 时间偏移量，单位为秒
     */
    fun selectPlatformStatsWithOffset(@Param("offset") offset: Int): List<PlatformStats?>

    /**
     * 获取所有消息数量
     */
    fun selectAllMsgCount(): Int
}