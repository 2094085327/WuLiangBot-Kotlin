package bot.wuliang.worldstate.parser

import com.fasterxml.jackson.databind.JsonNode

data class TraderOfferState(
    val item: String, val primePrice: Int?, val regularPrice: Int?, val itemPrices: List<CountedReward>,
)

data class TraderState(
    val id: String, val node: String, val activation: Long?, val expiry: Long?, val active: Boolean,
    val inventory: List<TraderOfferState>,
)

/** 解析商人状态及价格字段，不执行名称翻译或商品显示排序。 */
object TraderParser {
    /** 保留尚未到访的商人；常驻清单与轮换清单分别解析，去重只发生在各自清单内。 */
    fun parse(records: JsonNode, now: Long, manifestField: String = "Manifest"): List<TraderState> =
        records.arrayItems().filter { (it.timestamp("Expiry") ?: Long.MAX_VALUE) > now }.map { record ->
            TraderState(
                record.recordId(), record.text("Node"), record.timestamp("Activation"), record.timestamp("Expiry"),
                record.isActive(now),
                record.items(manifestField).distinctBy { it.text("ItemType") }.map { offer ->
                    TraderOfferState(
                        offer.text("ItemType"), offer.get("PrimePrice")?.asInt(), offer.get("RegularPrice")?.asInt(),
                        offer.items("ItemPrices")
                            .map { CountedReward(it.text("ItemType"), it.path("ItemCount").asInt()) },
                    )
                },
            )
        }
}