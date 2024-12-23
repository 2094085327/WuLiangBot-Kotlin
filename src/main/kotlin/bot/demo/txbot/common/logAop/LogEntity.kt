package bot.demo.txbot.common.logAop

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

@TableName("bot_log")
data class LogEntity(
    @Id
    @TableId(value = "id")
    val id: Int? = null,

    @TableField(value = "business_name")
    val businessName: String? = null,

    @TableField(value = "class_path")
    val classPath: String? = null,

    @TableField(value = "method_name")
    val methodName: String? = null,

    @TableField(value = "cmd_text")
    val cmdText: String? = null,

    @TableField(value = "event_type")
    val eventType: String? = null,

    @TableField(value = "group_id")
    val groupId: String? = null,

    @TableField(value = "user_id")
    val userId: String? = null,

    @TableField(value = "bot_id")
    val botId: Long? = null,

    @TableField(value = "cost_time")
    var costTime: Long? = null,

    @JsonDeserialize(using = LocalDateTimeDeserializer::class)
    @JsonSerialize(using = LocalDateTimeSerializer::class)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @TableField(value = "create_time")
    var createTime: LocalDateTime? = null,
)
