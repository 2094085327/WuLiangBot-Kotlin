package bot.demo.txbot.genShin.database.gachaLog

import com.baomidou.mybatisplus.extension.service.IService

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
     * 根据uid插入数据
     */
    fun insertByUid(
        uid: String,
        gachaType: String,
        itemId: String,
        count: String,
        time: String,
        name: String,
        lang: String,
        itemType: String,
        rankType: String,
        id: String,
        uigfGachaType: String
    )
}