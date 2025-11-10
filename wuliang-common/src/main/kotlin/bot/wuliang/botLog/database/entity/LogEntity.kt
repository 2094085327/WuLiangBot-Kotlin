package bot.wuliang.botLog.database.entity

import com.baomidou.mybatisplus.annotation.TableField
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName
import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer
import org.springframework.data.annotation.Id
import java.time.LocalDateTime

/**
 * 日志实体类
 * 对应数据库表: bot_log
 * 用于存储系统操作日志信息
 */
@TableName("bot_log")
data class LogEntity(
    /**
     * 主键ID
     * 数据库列: id
     */
    @Id
    @TableId(value = "id")
    val id: Int? = null,

    /**
     * 业务名称
     * 数据库列: business_name
     */
    @TableField(value = "business_name")
    val businessName: String? = null,

    /**
     * 类路径
     * 数据库列: class_path
     */
    @TableField(value = "class_path")
    val classPath: String? = null,

    /**
     * 方法名称
     * 数据库列: method_name
     */
    @TableField(value = "method_name")
    val methodName: String? = null,

    /**
     * 命令文本
     * 数据库列: cmd_text
     */
    @TableField(value = "cmd_text")
    val cmdText: String? = null,

    /**
     * 事件类型
     * 数据库列: event_type
     */
    @TableField(value = "event_type")
    val eventType: String? = null,

    /**
     * 群组ID
     * 数据库列: group_id
     */
    @TableField(value = "group_id")
    val groupId: String? = null,

    /**
     * 用户ID
     * 数据库列: user_id
     */
    @TableField(value = "user_id")
    val userId: String? = null,

    /**
     * 机器人ID
     * 数据库列: bot_id
     */
    @TableField(value = "bot_id")
    val botId: String? = null,

    /**
     * 耗时(毫秒)
     * 数据库列: cost_time
     */
    @TableField(value = "cost_time")
    var costTime: Long? = null,

    /**
     * 创建时间
     * 数据库列: create_time
     * JSON序列化格式: yyyy-MM-dd HH:mm:ss
     */
    @JsonDeserialize(using = LocalDateTimeDeserializer::class)
    @JsonSerialize(using = LocalDateTimeSerializer::class)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @TableField(value = "create_time")
    var createTime: LocalDateTime? = null,
)
