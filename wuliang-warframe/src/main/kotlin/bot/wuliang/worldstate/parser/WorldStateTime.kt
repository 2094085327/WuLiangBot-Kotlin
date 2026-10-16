package bot.wuliang.worldstate.parser

import com.fasterxml.jackson.databind.JsonNode
import java.time.Instant

/** 官方 Mongo 日期可采用数值或 $numberLong 包装形式，时间单位统一为毫秒。 */
object WorldStateTime {
    /** 读取 Mongo 日期中的数值或 `$numberLong` 包装并转换为 Unix 毫秒；缺项或无法解析时返回 null。 */
    fun millis(value: JsonNode?): Long? {
        val date = value?.path("\$date") ?: return null
        val timestamp = if (date.isObject) date.path("\$numberLong") else date
        return timestamp.asText().toLongOrNull()
    }

    /** 将官方 Mongo 日期转换为 [Instant]；没有有效时间戳时返回 null。 */
    fun instant(value: JsonNode?): Instant? = millis(value)?.let(Instant::ofEpochMilli)
}