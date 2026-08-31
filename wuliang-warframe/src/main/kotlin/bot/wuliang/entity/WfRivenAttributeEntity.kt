package bot.wuliang.entity

import com.baomidou.mybatisplus.annotation.TableField
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName
import java.io.Serializable

/**
 * Warframe 紫卡属性词库。
 */
@TableName("wf_riven_attribute")
data class WfRivenAttributeEntity(
    @TableId(value = "id")
    val id: String? = null,

    @TableField(value = "url_name")
    val urlName: String? = null,

    @TableField(value = "game_ref")
    val gameRef: String? = null,

    @TableField(value = "r_group")
    val rGroup: String? = null,

    @TableField(value = "prefix")
    val prefix: String? = null,

    @TableField(value = "suffix")
    val suffix: String? = null,

    @TableField(value = "unit")
    val unit: String? = null,

    @TableField(value = "en")
    val enName: String? = null,

    @TableField(value = "zh")
    val zhName: String? = null,
) : Serializable
