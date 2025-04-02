package bot.wuliang.service

import bot.wuliang.entity.LifeRestartEntity
import com.baomidou.mybatisplus.extension.service.IService

interface LifeRestartService : IService<LifeRestartEntity?> {
    /**
     * 插入用户开始游戏的次数
     *
     * @param realId 用户真实ID
     */
    fun insertTimesByRealId(realId: String)

    /**
     * 根据用户真实ID查询用户游戏信息
     *
     * @param realId 用户真实ID
     * @return 用户游戏信息
     */
    fun selectRestartInfoByRealId(realId: String): LifeRestartEntity?
}