package bot.wuliang.utils

import bot.wuliang.entity.WfRivenAttributeEntity
import com.fasterxml.jackson.databind.JsonNode

object WfRivenAttributeParser {
    fun parse(item: JsonNode): WfRivenAttributeEntity = WfRivenAttributeEntity(
        id = item["id"]?.textValue(),
        urlName = item["slug"]?.textValue(),
        gameRef = item["gameRef"]?.textValue(),
        rGroup = item["group"]?.textValue(),
        prefix = item["prefix"]?.textValue(),
        suffix = item["suffix"]?.textValue(),
        unit = item["unit"]?.textValue(),
        zhName = item["i18n"]?.get("zh-hans")?.get("name")?.textValue(),
        enName = item["i18n"]?.get("en")?.get("name")?.textValue(),
    )
}
