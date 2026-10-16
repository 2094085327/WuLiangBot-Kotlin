package bot.wuliang.worldstate.parser

import com.fasterxml.jackson.databind.JsonNode

/** 后台和赏金指令共用集团记录选择规则；重叠轮换取开始时间最新的一条。 */
object BountyParser {
    fun currentSyndicates(root: JsonNode, now: Long): Map<String, JsonNode> =
        root.path("SyndicateMissions").activeRecords(now).groupBy { it.text("Tag") }
            .mapValues { (_, records) -> records.maxBy { it.timestamp("Activation")!! } }
}