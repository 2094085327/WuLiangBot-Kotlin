package bot.demo.txbot.genShin.database.gachaLog

import com.baomidou.mybatisplus.annotation.TableField
import com.baomidou.mybatisplus.annotation.TableName


/**
 *@Description:
 *@Author zeng
 *@Date 2023/10/3 21:51
 *@User 86188
 */
@TableName("gachaloginfo3")
data class GaChaLogEntity(

    /**
     * 用户UID
     */
    @TableField(value = "uid")
    val uid: String? = null,

    /**
     * 卡池类型
     */
    @TableField(value = "gacha_type")
    val gachaType: String? = null,

    /**
     * 物品Id
     */
    @TableField(value = "item_id")
    val itemId: String? = null,

    /**
     * 物品获取量
     */
    @TableField(value = "count")
    val count: String? = null,

    /**
     * 获取时间
     */
    @TableField(value = "time")
    val time: String? = null,

    /**
     * 物品名称
     */
    @TableField(value = "name")
    val name: String? = null,

    /**
     * 物品语言
     */
    @TableField(value = "lang")
    val lang: String? = null,

    /**
     * 物品类型
     */
    @TableField(value = "item_type")
    val itemType: String? = null,

    /**
     * 物品等级
     */
    @TableField(value = "rank_type")
    val rankType: String? = null,

    /**
     * 记录唯一ID
     */
    @TableField(value = "id")
    val id: String? = null,


    /**
     * uigf统一规范卡池类型
     */
    @TableField(value = "uigf_gacha_type")
    val uigfGachaType: String? = null,
)