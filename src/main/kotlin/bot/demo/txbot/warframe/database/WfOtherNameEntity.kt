package bot.demo.txbot.warframe.database

import com.baomidou.mybatisplus.annotation.TableField
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName

@TableName("wf_other_name")
data class WfOtherNameEntity(
    /***
     * 别名ID
     */
    @TableId(value = "id")
    val id: Int? = null,

    /**
     * 英文名
     */
    @TableField(value = "en_item_name")
    val enItemName: String? = null,

    /**
     * 别名
     */
    @TableField(value = "other_name")
    val otherName: String? = null,
)
