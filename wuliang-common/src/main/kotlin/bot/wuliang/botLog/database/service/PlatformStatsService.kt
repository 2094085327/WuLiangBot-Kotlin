package bot.wuliang.botLog.database.service

import bot.wuliang.botLog.database.entity.PlatformStats

interface PlatformStatsService {
    /**
     * 插入或更新平台统计数据
     *
     * @param platformStats 平台统计实体类
     */
    fun insertOrUpdatePlatformStats(platformStats: PlatformStats)

    /**
     * 获取对应时间范围的平台统计数据
     *
     * @param offset 时间偏移量，单位为秒
     */
    fun getPlatformStatsWithOffset(offset: Int): List<PlatformStats?>

     /**
      * 获取所有消息数量
      *
      */
    fun getAllMsgCount(): Int
}