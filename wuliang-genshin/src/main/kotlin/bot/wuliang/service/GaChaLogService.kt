package bot.wuliang.service

import bot.wuliang.entity.GaChaLogEntity
import com.baomidou.mybatisplus.extension.service.IService
import com.fasterxml.jackson.databind.JsonNode

/**
 *@Description:
 *@Author zeng
 *@Date 2023/10/3 22:25
 *@User 86188
 */

interface GaChaLogService : IService<GaChaLogEntity?> {
    /**
     * 根据uid查询数据
     */
    fun selectByUid(uid: String): Int?

    /**
     * 向数据库中插入数据
     *
     * @param gachaData 抽卡数据
     * @return 是否为最后的数据
     */
    fun insertByJson(gachaData: JsonNode): Boolean
}