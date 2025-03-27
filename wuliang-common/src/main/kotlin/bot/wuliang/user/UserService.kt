package bot.wuliang.user

import com.baomidou.mybatisplus.extension.service.IService

/**
 *@Description: 用户服务
 *@Author zeng
 *@Date 2023/10/3 22:25
 *@User 86188
 */
interface UserService : IService<UserEntity?> {
    /**
     * 根据真实ID查询原神UID
     *
     * @param realId 真实ID
     * @return 原神UID
     */
    fun selectGenUidByRealId(realId: String): String?

    /**
     * 插入原神UID
     *
     * @param realId 真实ID
     * @param genUid 原神UID
     */
    fun insertGenUidByRealId(realId: String, genUid: String)
}