package bot.wuliang.worldstate.presentation

import bot.wuliang.worldstate.model.WorldEntry
import bot.wuliang.worldstate.model.WorldSection
import bot.wuliang.worldstate.parser.TraderParser
import com.fasterxml.jackson.databind.JsonNode

/** 商品清单共享解析，面板只增加商品价格文本和到访/发布状态提示。 */
internal class TraderSections(private val display: WorldStateDisplay) {
    /**
     * 组装商人商品和到访提示，常驻清单不显示轮换结束时间。
     *
     * @param root 完整官方世界状态。
     * @param now 本次查询的 Unix 毫秒时间。
     * @param prime 是否使用 PrimeVaultTraders 数据。
     * @param manifestField 商品清单字段，轮换清单为 Manifest，常驻清单为 EvergreenManifest。
     */
    fun traders(root: JsonNode, now: Long, prime: Boolean, manifestField: String = "Manifest"): WorldSection {
        val evergreen = prime && manifestField == "EvergreenManifest"
        val sectionId = if (!prime) "trader" else if (evergreen) "varzia-evergreen" else "varzia"
        val title = if (!prime) "虚空商人" else if (evergreen) "瓦奇娅 · 常驻商品" else "瓦奇娅 · 轮换商品"
        val category = if (prime) "瓦奇娅" else "虚空商人"
        val entries =
            TraderParser.parse(root.path(if (prime) "PrimeVaultTraders" else "VoidTraders"), now, manifestField)
                .flatMapIndexed { traderIndex, trader ->
                    val expiry = if (evergreen) null else trader.expiry
                    if (trader.inventory.isEmpty() || (trader.activation != null && trader.activation > now)) {
                        listOf(
                            WorldEntry(
                                "$sectionId-$traderIndex-unavailable", category, category, display.node(trader.node),
                                details = listOf(if (trader.activation != null && trader.activation > now) "尚未到访" else "官方暂未发布商品"),
                                activation = trader.activation, expiry = expiry,
                            )
                        )
                    } else trader.inventory.mapIndexed { index, offer ->
                        val prices = buildList {
                            offer.primePrice?.let { add("$it ${if (prime) "御品阿耶精华" else "杜卡德金币"}") }
                            offer.regularPrice?.let { add("$it ${if (prime) "阿耶精华" else "现金"}") }
                            addAll(offer.itemPrices.map { "${display.name(it.item)} ×${it.count}" })
                        }
                        WorldEntry(
                            "$sectionId-$traderIndex-$index",
                            display.name(offer.item),
                            category,
                            display.node(trader.node),
                            details = prices,
                            activation = trader.activation,
                            expiry = expiry
                        )
                    }
                }
        return WorldSection(sectionId, title, entries)
    }
}