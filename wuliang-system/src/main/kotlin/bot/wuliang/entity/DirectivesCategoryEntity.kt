package bot.wuliang.entity

import com.alibaba.fastjson2.annotation.JSONField
import com.baomidou.mybatisplus.annotation.FieldFill
import com.baomidou.mybatisplus.annotation.TableField
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName
import com.fasterxml.jackson.annotation.JsonFormat
import java.io.Serializable
import java.util.*

@TableName("directives_category")
data class DirectivesCategoryEntity(
    /**
     * 主键
     */
    @TableId(value = "id")
    var id: Int? = null,

    /**
     * 分类名称
     */
    @TableField(value = "category_name")
    var categoryName: String? = null,

    /**
     * 分类描述
     */
    @TableField(value = "category_desc")
    var categoryDesc: String? = null,

    /**
     * 删除状态
     */
    @TableField(value = "del_status", fill = FieldFill.INSERT)
    var delStatus: Int? = 0,

    /** 创建时间  */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @JSONField(format = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    private var createTime: Date? = null,

    /** 更新时间  */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @JSONField(format = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "update_time", fill = FieldFill.INSERT)
    private var updateTime: Date? = null
) : Serializable
