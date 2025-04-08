package bot.wuliang.entity

import com.alibaba.fastjson2.annotation.JSONField
import com.baomidou.mybatisplus.annotation.*
import com.fasterxml.jackson.annotation.JsonFormat
import java.io.Serializable
import java.util.*

@TableName(value = "bot_config")
data class BotConfigEntity(

    /**
     * 主键
     */
    @TableId(type = IdType.AUTO)
    var id: Int? = null,

    /**
     * 配置名称
     */
    @TableField(value = "config_name")
    var configName: String? = null,

    /**
     * 配置键名
     */
    @TableField(value = "config_key")
    var configKey: String? = null,

    /**
     * 配置值
     */
    @TableField(value = "config_value")
    var configValue: String? = null,


    /**
     * 状态
     */
    @TableField(value = "status")
    var status: Int? = null,

    /**
     * 创建时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @JSONField(format = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    var createTime: Date? = null,

    /**
     * 更新时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @JSONField(format = "yyyy-MM-dd HH:mm:ss")
    @TableField(value = "update_time", fill = FieldFill.UPDATE)
    var updateTime: Date? = null,

    /**
     * 删除状态
     */
    @TableField(value = "del_status", fill = FieldFill.INSERT)
    var delStatus: Int? = null,

    /**
     * 备注
     */
    @TableField(value = "remark")
    var remark: String? = null
) : Serializable

