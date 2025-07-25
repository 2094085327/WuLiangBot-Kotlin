package bot.wuliang.entity

import com.baomidou.mybatisplus.annotation.TableField
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName

/**
 * Warframe 词库
 *
 * @property id 词条ID
 * @property enItemName 英文词条名
 * @property zhItemName 中文词条名
 * @property urlName 词条URL名
 */
@TableName("wf_lexicon")
data class WfLexiconEntity(
    /***
     * 词条ID
     */
    @TableId(value = "id")
    val id: String? = null,
    /**
     * 英文词条名
     */
    @TableField("en_item_name")
    var enItemName: String? = null,
    /**
     * 中文词条名
     */
    @TableField("zh_item_name")
    var zhItemName: String? = null,
    /**
     * 词条URL名
     */
    @TableField("url_name")
    val urlName: String? = null,

    /**
     * 是否在市场中
     */
    @TableField("in_market")
    val inMarket: Int? = null,

    /**
     * 被使用到的次数
     */
    @TableField("use_count")
    var useCount: Int? = 0,
)
