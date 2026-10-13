package bot.wuliang.worldstate

import com.fasterxml.jackson.databind.JsonNode
import java.time.Instant

/** 官方 Mongo 日期可采用数值或 $numberLong 包装形式，时间单位统一为毫秒。 */
object WorldStateTime {
    fun millis(value: JsonNode?): Long? {
        val date = value?.path("\$date") ?: return null
        val timestamp = if (date.isObject) date.path("\$numberLong") else date
        return timestamp.asText().toLongOrNull()
    }

    fun instant(value: JsonNode?): Instant? = millis(value)?.let(Instant::ofEpochMilli)
}