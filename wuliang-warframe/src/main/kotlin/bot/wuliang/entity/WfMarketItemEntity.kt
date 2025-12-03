package bot.wuliang.entity

import com.baomidou.mybatisplus.annotation.TableField
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName
import java.io.Serializable

@TableName("wf_market_item")
data class WfMarketItemEntity(
    /***
     * 词条ID
     */
    @TableId(value = "id")
    val id: String? = null,
    /**
     * 英文词条名
     */
    @TableField("en_name")
    var enName: String? = null,
    /**
     * 中文词条名
     */
    @TableField("zh_name")
    var zhName: String? = null,
    /**
     * 词条URL名
     */
    @TableField("url_name")
    var urlName: String? = null,

    /**
     * 物品对应的杜卡德金币值
     * 可能为null
     */
    @TableField("ducats")
    var ducats: Int? = null,

    /**
     * 被使用到的次数
     */
    @TableField("use_count")
    var useCount: Int? = 0,
) : Serializable
