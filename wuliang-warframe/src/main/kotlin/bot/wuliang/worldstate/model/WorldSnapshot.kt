package bot.wuliang.worldstate.model

import com.fasterxml.jackson.databind.JsonNode

/** fetchedAt 是本地成功获取时间；stale 表示获取失败后返回了旧快照。 */
data class WorldSnapshot(val data: JsonNode, val fetchedAt: Long, val stale: Boolean = false)