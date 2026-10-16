package bot.wuliang.entity

import com.baomidou.mybatisplus.annotation.*
import com.fasterxml.jackson.annotation.JsonFormat
import java.io.Serializable
import java.util.*

@TableName("bot_directives")
data class DirectivesEntity(
    /***
     * 指令ID
     */

    @TableId(value = "id", type = IdType.AUTO)
    var id: Long? = null,

    /**
     * 分类ID
     */
    @TableField(value = "category_id")
    val categoryId: Long? = null,

    /**
     * 分类名称
     */
    @TableField(exist = false)
    val categoryName: String? = null,

    /**
     * 分类描述
     */
    @TableField(exist = false)
    val categoryDesc: String? = null,

    /**
     * 指令名称
     */
    @TableField(value = "directive_name")
    val directiveName: String? = null,

    /**
     * 指令显示描述
     */
    @TableField(value = "description")
    val description: String? = null,

    /**
     * 指令详细描述
     */
    @TableField(value = "detail")
    val detail: String? = null,


    /**
     * 指令正则
     */
    @TableField(value = "regex")
    val regex: String? = null,

    /**
     * 是否启用
     */
    @TableField(value = "enable", fill = FieldFill.INSERT)
    val enable: Int? = null,

    /**
     * 删除状态
     */
    @TableField(value = "del_status", fill = FieldFill.INSERT)
    val delStatus: Int? = null,


    /** 创建时间  */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private var createTime: Date? = null,

    /** 更新时间  */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @TableField(value = "update_time", fill = FieldFill.UPDATE)
    private var updateTime: Date? = null

) : Serializable
