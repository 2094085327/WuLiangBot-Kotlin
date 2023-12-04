package bot.demo.txbot.genShin.database.genshin

import com.baomidou.mybatisplus.annotation.TableField
import com.baomidou.mybatisplus.annotation.TableName
import java.time.LocalDateTime

/**
 *@Description:
 *@Author zeng
 *@Date 2023/10/4 11:26
 *@User 86188
 */
@TableName("genshininfo")
data class GenShinEntity(
    /**
     * 用户UID
     */
    @TableField(value = "uid") val uid: String? = null,

    /**
     * 用户QQID
     */
    @TableField(value = "qqid")
    private val qqId: String? = null,

    /**
     * 原神游戏昵称
     */
    @TableField(value = "nickname")
    private val nickname: String? = null,

    /**
     * 原神签到cookie
     */
    @TableField(value = "cookie")
    private val cookie: String? = null,

    /**
     * 抽卡分析sToken
     */
    @TableField(value = "stoken") val sToken: String? = null,

    /**
     * 是否推送
     */
    @TableField(value = "push")
    private val push: Int? = null,

    /**
     * 是否废弃
     */
    @TableField(value = "deletes")
    private val deletes: Int? = null,

    /**
     * 0为失效，1为可用
     */
    @TableField(value = "status") val status: Int? = null,

    /**
     * 更新时间
     */
    @TableField(value = "updatetime") val updateTime: LocalDateTime? = null

)
