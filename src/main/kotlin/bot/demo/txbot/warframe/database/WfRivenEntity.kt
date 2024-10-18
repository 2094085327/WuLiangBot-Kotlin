package bot.demo.txbot.warframe.database

import com.baomidou.mybatisplus.annotation.TableField
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName


/**
 * @description: Warframe 紫卡实体类
 * @author Nature Zero
 * @date 2024/5/21 下午11:23
 */
@TableName("wf_riven")
data class WfRivenEntity(
    /***
     * 词条ID
     */
    @TableId(value = "id")
    val id: String,

    /**
     * 词条URL名
     */
    @TableField(value = "url_name")
    val urlName: String,

    /**
     * 英文词条名
     */
    @TableField(value = "en")
    var enName: String? = null,

    /**
     * 中文词条名
     */
    @TableField(value = "zh")
    var zhName: String? = null,

    /**
     * 紫卡组
     */
    @TableField(value = "r_group")
    val rGroup: String,

    /**
     * 是否为紫卡属性
     */
    @TableField(value = "attributes")
    val attributesBool: Int? = 0
)