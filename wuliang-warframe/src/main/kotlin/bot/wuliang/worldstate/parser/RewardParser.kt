package bot.wuliang.worldstate.parser

import com.fasterxml.jackson.databind.JsonNode

data class CountedReward(val item: String, val count: Int)

data class RewardState(val items: List<String>, val countedItems: List<CountedReward>, val credits: Int)

/** 奖励保留物品协议标识与数量；翻译及排版不参与协议解析。 */
object RewardParser {
    /** 解析奖励中的普通物品、带数量物品和现金，不翻译名称或生成展示文本。 */
    fun parse(record: JsonNode): RewardState = RewardState(
        record.items("items").map { it.asText() },
        record.items("countedItems").map { CountedReward(it.text("ItemType"), it.path("ItemCount").asInt()) },
        record.path("credits").asInt(),
    )
}