package bot.wuliang.entity

import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableField
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName


/**
 * @description: 人生重开实体类
 * @author Nature Zero
 * @date 2024/3/25 11:42
 */
@TableName("lifeRestartInfo")
data class LifeRestartEntity(
    @TableId(value = "id", type = IdType.AUTO)
    private val id: Int? = null,

    /**
     * 用户真实ID（唯一标识）
     */
    @TableField(value = "real_id")
    private val realId: String? = null,

    /**
     * 用户开始游戏的次数
     */
    @TableField(value = "times") var times: Int? = null,

    /**
     * 用户达成的游戏成就数
     */
    @TableField(value = "cachv") val cachv: Int? = null,

    /**
     * 用户达成的游戏成就
     */
    @TableField(value = "achv")
    private val achv: String? = null,
)