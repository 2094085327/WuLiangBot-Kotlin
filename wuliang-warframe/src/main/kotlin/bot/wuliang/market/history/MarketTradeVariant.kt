package bot.wuliang.market.history

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory

/** 成交规格字段归一，确保等级、镶星及充能分别统计。 */
object MarketTradeVariant {
    private val aliases = linkedMapOf(
        "rank" to listOf("rank", "modRank", "mod_rank"),
        "subtype" to listOf("subtype"),
        "amberStars" to listOf("amberStars", "amber_stars"),
        "cyanStars" to listOf("cyanStars", "cyan_stars"),
        "charges" to listOf("charges"),
    )

    fun json(node: JsonNode): String {
        val variant = JsonNodeFactory.instance.objectNode()
        for ((field, names) in aliases) {
            val value = names.firstNotNullOfOrNull { node[it]?.takeUnless(JsonNode::isNull) } ?: continue
            if (field == "subtype") require(value.isTextual && value.asText().isNotBlank()) { "WM 成交规格无效" }
            else require(value.isIntegralNumber && value.canConvertToInt() && value.intValue() >= 0) { "WM 成交规格数值无效" }
            variant.set<JsonNode>(field, value)
        }
        return variant.toString()
    }
}