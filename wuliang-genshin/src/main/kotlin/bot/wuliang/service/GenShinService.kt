package bot.wuliang.service

import bot.wuliang.entity.GenShinEntity
import com.baomidou.mybatisplus.extension.service.IService

/**
 *@Description:
 *@Author zeng
 *@Date 2023/10/4 11:24
 *@User 86188
 */
interface GenShinService : IService<GenShinEntity?> {
    /**
     * 根据uid插入数据
     */
    fun insertByUid(uid: String, qqId: String? = null, nickName: String)

    /**
     *根据Uid获取数据
     */
    fun selectByUid(uid: String): String

    /**
     * 根据QQ号查询数据
     */
    fun selectByQqId(qqId: String): String

    /**
     * 根据QQ号获取uid
     */
    fun getUidByQqId(qqId: String): String
}