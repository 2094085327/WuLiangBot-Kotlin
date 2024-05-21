package bot.demo.txbot.warframe.database

import com.baomidou.mybatisplus.annotation.TableField
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName


/**
 * @description: Warframe 紫卡实体类
 * @author Nature Zero
 * @date 2024/5/21 下午11:23
 */
@TableName("wfRiven")
data class WfRivenEntity(
    /***
     * 词条ID
     */
    @TableId(value = "id")
    val id: String,

    /**
     * 词条URL名
     */
    @TableField("url_name")
    val urlName: String,

    /**
     * 英文词条名
     */
    @TableField("en")
    var enName: String? = null,

    /**
     * 中文词条名
     */
    @TableField("zh")
    var zhName: String? = null,

    /**
     * 紫卡组
     */
    @TableField("group")
    val group: String
)