package bot.demo.txbot.warframe.database

import com.baomidou.mybatisplus.annotation.TableField
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName
import com.fasterxml.jackson.annotation.JsonProperty


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
    @JsonProperty("id")
    @TableId(value = "id")
    val id: String? = null,

    /**
     * 词条URL名
     */
    @JsonProperty("urlName")
    @TableField(value = "url_name")
    val urlName: String? = null,

    /**
     * 英文词条名
     */
    @JsonProperty("enName")
    @TableField(value = "en")
    var enName: String? = null,

    /**
     * 中文词条名
     */
    @JsonProperty("zhName")
    @TableField(value = "zh")
    var zhName: String? = null,

    /**
     * 紫卡组
     */
    @JsonProperty("rgroup")
    @TableField(value = "r_group")
    val rGroup: String? = null,

    /**
     * 是否为紫卡属性
     */
    @JsonProperty("attributes")
    @TableField(value = "attributes")
    val attributesBool: Int? = 0
)