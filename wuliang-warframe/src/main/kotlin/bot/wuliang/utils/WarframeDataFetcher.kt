package bot.wuliang.utils

import bot.wuliang.config.WARFRAME_DROP_DATA_URL
import bot.wuliang.httpUtil.HttpUtil
import bot.wuliang.parser.model.DropData
import org.springframework.stereotype.Component

/** 保留现有掉率数据入口，供后续与 Plus 奖励关系关联；当前没有自动调用方。 */
@Component
class WarframeDataFetcher {
    /** 读取 WarframeStat 提供的精简掉率数据。 */
    fun fetchDropRates(): List<DropData> {
        val raw = HttpUtil.doGetJson(WARFRAME_DROP_DATA_URL)
        require(raw.isArray) { "Warframe 掉落数据格式错误" }
        return raw.mapNotNull { node ->
            val place = node["place"]?.asText()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val item = node["item"]?.asText()?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val chance = node["chance"]?.asDouble() ?: return@mapNotNull null
            val rarity = node["rarity"]?.asText().orEmpty()
            DropData(
                location = place.replace("<b>", "").replace("</b>", ""),
                type = item,
                chance = chance,
                rarity = rarity,
                rotation = Regex("Rotation\\s+([A-Z])", RegexOption.IGNORE_CASE)
                    .find(place)?.groupValues?.getOrNull(1)
            )
        }
    }
}