package bot.demo.txbot.common.database.user

import com.baomidou.mybatisplus.annotation.*


/**
 *@Description: 用户实体类
 *@Author zeng
 *@Date 2023/10/3 21:51
 *@User 86188
 */
@TableName("user")
data class UserEntity(
    @TableId(value = "id", type = IdType.AUTO)
    private val id: Int? = null,

    /**
     * 用户真实ID（唯一标识）
     */
    @TableField(value = "real_id")
    private val realId: String? = null,

    /**
     * 用户原神UID
     */
    @TableField(value = "genshin_uid") val genUid: String? = null,

    )