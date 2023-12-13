package bot.demo.txbot.genShin.database.gacha

import com.baomidou.mybatisplus.extension.service.IService

/**
 *@Description:
 *@Author zeng
 *@Date 2023/10/3 22:25
 *@User 86188
 */

interface GaChaService : IService<GaChaEntity?> {
    /**
     * 根据uid查询数据
     */
    fun selectByUid(uid: String):Int?

    /**
     * 根据uid插入数据
     */
    fun insertByUid(
        uid: String,
        type: String,
        itemName: String,
        times: Int = 0,
        itemType: String,
        rankType: Int,
        itemId: String,
        getTime: String,
    )
}