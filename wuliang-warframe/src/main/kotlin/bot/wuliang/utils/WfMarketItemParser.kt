package bot.wuliang.utils

import bot.wuliang.entity.WfMarketItemEntity
import bot.wuliang.jacksonUtil.JacksonUtil
import com.fasterxml.jackson.databind.JsonNode

/** Market 物品名称使用其自身词库，保留官方游戏词库没有的套装等交易组合。 */
object WfMarketItemParser {
    fun parse(item: JsonNode): WfMarketItemEntity {
        val englishName = item["i18n"]?.get("en")?.get("name")?.textValue()?.takeIf { it.isNotBlank() }
        val chineseName = item["i18n"]?.get("zh-hans")?.get("name")?.textValue()?.takeIf { it.isNotBlank() }
        val tags = item["tags"]?.takeIf { it.isArray }?.mapNotNull { it.textValue() }.orEmpty()
        return WfMarketItemEntity(
            id = item["id"].textValue(),
            urlName = item["slug"].textValue(),
            gameRef = item["gameRef"]?.textValue(),
            tags = JacksonUtil.toJsonString(tags),
            // 仅在 Market 自身缺少中文时回退英文或 slug，不再用单件物品译名覆盖套装。
            zhName = chineseName ?: englishName ?: item["slug"].textValue(),
            enName = englishName,
            ducats = item["ducats"]?.intValue(),
        )
    }
}