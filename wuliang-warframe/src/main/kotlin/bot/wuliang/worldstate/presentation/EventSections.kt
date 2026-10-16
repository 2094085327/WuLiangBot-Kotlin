package bot.wuliang.worldstate.presentation

import bot.wuliang.worldstate.model.WorldEntry
import bot.wuliang.worldstate.model.WorldSection
import bot.wuliang.worldstate.parser.items
import bot.wuliang.worldstate.parser.text
import com.fasterxml.jackson.databind.JsonNode

/** 活动、警报和特惠的面板排版；奖励与有效期沿用统一协议规则。 */
internal class EventSections(private val display: WorldStateDisplay) {
    /** 将当前有效的活动与警报转换为面板条目，使用统一名称和奖励显示规则。 */
    fun events(root: JsonNode, now: Long): WorldSection = WorldSection(
        "events", "活动与警报",
        display.current(root, "Goals", now).mapIndexed { i, v ->
            WorldEntry(
                "goal-$i",
                display.name(v.text("Desc")),
                "活动",
                v.text("Node").takeIf(String::isNotEmpty)?.let(display::node).orEmpty(),
                listOfNotNull(v["ToolTip"]?.asText()?.let(display::name)),
                display.rewardObject(v.path("Reward")),
                display.date(v["Activation"]),
                display.date(v["Expiry"])
            )
        } + display.current(root, "Alerts", now).mapIndexed { i, v ->
            val m = v.path("MissionInfo")
            WorldEntry(
                "alert-$i",
                display.node(m.text("location")),
                "警报",
                display.name(m.text("missionType")),
                rewards = display.rewardObject(m.path("missionReward")),
                activation = display.date(v["Activation"]),
                expiry = display.date(v["Expiry"])
            )
        })

    /** 组装当前有效的每日特惠与商店闪购，过滤隐藏商品并展示价格和库存信息。 */
    fun deals(root: JsonNode, now: Long): WorldSection = WorldSection(
        "deals", "限时特惠",
        display.current(root, "DailyDeals", now).mapIndexed { i, v ->
            WorldEntry(
                "daily-$i", display.name(v.text("StoreItem")), "达尔沃特惠", "${v.path("SalePrice").asInt()} 白金",
                listOf(
                    "原价 ${v.path("OriginalPrice").asInt()} 白金 · 优惠 ${v.path("Discount").asInt()}%",
                    "剩余 ${(v.path("AmountTotal").asInt() - v.path("AmountSold").asInt()).coerceAtLeast(0)} / ${
                        v.path(
                            "AmountTotal"
                        ).asInt()
                    }"
                ),
                activation = display.date(v["Activation"]), expiry = display.date(v["Expiry"])
            )
        } + root.items("FlashSales").filter {
            !it.path("HideFromMarket").asBoolean() && (display.date(it["StartDate"])
                ?: 0) <= now && (display.date(it["EndDate"])
                ?: Long.MAX_VALUE) > now
        }.mapIndexed { i, v ->
            WorldEntry(
                "flash-$i", display.name(v.text("TypeName")), "商店特惠", details = listOfNotNull(
                    v["PremiumOverride"]?.asInt()?.let { "$it 白金" },
                    v["Discount"]?.asDouble()?.let { "折扣参数 $it" }),
                activation = display.date(v["StartDate"]), expiry = display.date(v["EndDate"])
            )
        })
}
