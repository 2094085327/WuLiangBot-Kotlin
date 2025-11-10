package bot.wuliang.botLog.database.entity

import com.baomidou.mybatisplus.annotation.TableField
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName
import com.fasterxml.jackson.annotation.JsonFormat
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer
import java.time.LocalDateTime

/**
 * 平台统计数据实体类
 * 对应数据库表: bot_platform_stats
 * 用于存储平台相关的统计信息
 */
@TableName("bot_platform_stats")
data class PlatformStats(
    /**
     * 主键ID
     * 数据库列: id
     */
    @TableId(value = "id")
    val id: Int? = null,

    /**
     * 统计时间戳
     * 数据库列: timestamp
     * JSON序列化格式: yyyy-MM-dd HH:mm:ss
     */
    @JsonDeserialize(using = LocalDateTimeDeserializer::class)
    @JsonSerialize(using = LocalDateTimeSerializer::class)
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @TableField(value = "timestamp")
    var timestamp: LocalDateTime? = null,

    /**
     * 统计数量
     * 数据库列: count
     */
    @TableField(value = "count")
    var count: Int? = null
)
