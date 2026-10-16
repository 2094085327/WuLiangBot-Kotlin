package bot.wuliang.worldstate.parser

import com.fasterxml.jackson.databind.JsonNode

/** 官方协议的基础读取规则。这里只解释数据，不访问网络、Redis 或展示词库。 */
internal fun JsonNode.text(key: String): String = path(key).asText("")

/** 读取 [key] 对应的 JSON 数组；字段不存在或不是数组时返回空列表。 */
internal fun JsonNode.items(key: String): List<JsonNode> = path(key).arrayItems()

/** 将当前 JSON 数组转为列表；非数组节点返回空列表。 */
internal fun JsonNode.arrayItems(): List<JsonNode> = takeIf { isArray }?.toList().orEmpty()

/** 读取官方记录的 `_id.$oid` 标识；缺少标识时返回空字符串。 */
internal fun JsonNode.recordId(): String = path("_id").text("\$oid")

/** 读取 [key] 对应的官方 Mongo 日期并转换为 Unix 毫秒；格式无效时返回 null。 */
internal fun JsonNode.timestamp(key: String): Long? = WorldStateTime.millis(get(key))

/** 时间区间统一采用 [Activation, Expiry)，缺少边界的轮换记录不作为有效任务。 */
internal fun JsonNode.isActive(now: Long): Boolean {
    val activation = timestamp("Activation") ?: return false
    val expiry = timestamp("Expiry") ?: return false
    return now in activation..<expiry
}

/** 筛选在 [now] 这个 Unix 毫秒时刻生效的记录，统一采用左闭右开的时间区间。 */
internal fun JsonNode.activeRecords(now: Long): List<JsonNode> = arrayItems().filter { it.isActive(now) }

/** 上游跨轮换可能同时发布两条有效记录，单轮玩法统一取开始时间最新的一条。 */
internal fun JsonNode.latestActive(now: Long): JsonNode? =
    activeRecords(now).maxByOrNull { it.timestamp("Activation")!! }

/** 缓存到最近的轮换边界；缺项或发布延迟时短缓存，避免冻结上一轮结果。 */
internal fun JsonNode.rotationCacheSeconds(now: Long): Long {
    return rotationCacheSeconds(arrayItems(), now)
}

/** 根据有效记录的激活与结束边界计算缓存剩余秒数；无有效记录时返回三十秒重试间隔。 */
internal fun rotationCacheSeconds(records: Iterable<JsonNode>, now: Long): Long {
    if (records.none { it.isActive(now) }) return 30
    return records.flatMap { listOfNotNull(it.timestamp("Activation"), it.timestamp("Expiry")) }
        .filter { it > now }.minOfOrNull { ((it - now) / 1000).coerceAtLeast(1) } ?: 30
}