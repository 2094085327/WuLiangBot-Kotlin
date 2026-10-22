package bot.wuliang.market.presentation

import bot.wuliang.market.MarketItemLookupService
import bot.wuliang.market.history.MarketHistoryService
import bot.wuliang.market.history.MarketTradeVariant
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.OffsetDateTime

class MarketTrendUnavailableException(message: String) : RuntimeException(message)

data class MarketTrendPoint(
    val timestamp: Long,
    val average: BigDecimal?,
    val median: BigDecimal?,
    val volume: Long,
)

data class MarketTrendSummary(
    val window: String,
    val volume: Long,
    val estimatedTurnover: BigDecimal?,
    val average: BigDecimal?,
    val latestMedian: BigDecimal?,
)

data class MarketTrendReport(
    val name: String,
    val slug: String,
    val platform: String,
    val window: String,
    val variantLabel: String,
    val fetchedAt: Long,
    val points: List<MarketTrendPoint>,
    val summaries: List<MarketTrendSummary>,
)

/** 名称解析复用 wm；对两个历史窗口使用同一个完整规格，绝不合并不同等级或镶星。 */
@Service
class MarketTrendService(
    private val lookup: MarketItemLookupService,
    private val history: MarketHistoryService,
    private val mapper: ObjectMapper,
) {
    private data class Record(val variant: String, val point: MarketTrendPoint)

    fun query(request: MarketTrendRequest): MarketTrendReport {
        val item = lookup.fetchItemEntity(request.item)
            ?: throw MarketTrendUnavailableException("未找到「${request.item}」，请使用市场物品名称或已配置的别名。")
        val slug = item.urlName?.takeIf(String::isNotBlank)
            ?: throw MarketTrendUnavailableException("该物品暂时没有市场历史数据。")
        val maxRank = item.maxRank
        val selectedRank = when {
            request.maxRank -> maxRank
                ?: throw MarketTrendUnavailableException("「${item.zhName ?: request.item}」不支持等级筛选。")
            request.rank != null -> {
                if (maxRank == null || request.rank > maxRank) {
                    throw MarketTrendUnavailableException(
                        if (maxRank == null) "该物品不支持等级筛选。" else "该物品仅支持0–${maxRank}级。"
                    )
                }
                request.rank
            }
            maxRank != null -> 0
            else -> null
        }
        val windows = history.queryWindows(slug)
        val records = windows.mapValues { (_, result) -> result.points.mapNotNull(::parse) }
        val variants = records.values.flatten().map(Record::variant).distinct()
            .map { it to mapper.readTree(it) }
            .filter { (_, variant) ->
                if (selectedRank != null) variant["rank"]?.asInt() == selectedRank else !variant.has("rank")
            }
        // 雕像等优先低镶星规格；同一规格同时用于90天、48小时摘要和曲线。
        val variant = variants.sortedWith(
            compareBy<Pair<String, JsonNode>> { it.second.path("amberStars").asInt() + it.second.path("cyanStars").asInt() }
                .thenBy { it.second.path("charges").asInt() }.thenBy { it.first }
        ).firstOrNull()
        val rankLabel = selectedRank?.let { if (request.maxRank) "满级（${it}级）" else "${it}级" }
        if (variant == null) throw MarketTrendUnavailableException("「${item.zhName ?: request.item}」${rankLabel.orEmpty()}暂无已成交历史。")
        val selected = records.mapValues { (_, rows) ->
            rows.filter { it.variant == variant.first }.map(Record::point).sortedBy(MarketTrendPoint::timestamp)
                .also { points -> check(points.distinctBy { it.timestamp }.size == points.size) { "同时间同规格存在重复成交统计" } }
        }
        val labels = buildList {
            rankLabel?.let(::add)
            variant.second["amberStars"]?.let { add("琥珀星${it.asInt()}") }
            variant.second["cyanStars"]?.let { add("青蓝星${it.asInt()}") }
            variant.second["subtype"]?.let { add(it.asText()) }
            variant.second["charges"]?.let { add("充能${it.asInt()}") }
        }
        val snapshot = windows.getValue(request.window)
        return MarketTrendReport(
            name = item.zhName ?: item.enName ?: request.item, slug = slug,
            platform = snapshot.platform, window = request.window,
            variantLabel = labels.joinToString(" · "), fetchedAt = snapshot.fetchedAt,
            points = selected.getValue(request.window),
            summaries = listOf("48hours", "90days").map { summary(it, selected.getValue(it)) },
        )
    }

    private fun parse(row: JsonNode): Record? {
        val orderType = row["order_type"]?.takeUnless(JsonNode::isNull)?.asText()
        check(orderType == null || orderType == "closed") { "历史统计中出现非成交记录" }
        val volume = row["volume"]
        check(volume != null && volume.isIntegralNumber && volume.canConvertToLong() && volume.longValue() >= 0) {
            "历史成交量无效"
        }
        if (volume.longValue() == 0L) return null
        val timestamp = OffsetDateTime.parse(row.path("datetime").asText()).toInstant().toEpochMilli()
        fun price(field: String): BigDecimal? = row[field]?.takeUnless(JsonNode::isNull)?.let {
            check(it.isNumber && it.decimalValue().signum() >= 0) { "历史价格无效" }
            it.decimalValue()
        }
        return Record(MarketTradeVariant.json(row), MarketTrendPoint(timestamp, price("wa_price"), price("median"), volume.longValue()))
    }

    private fun summary(window: String, points: List<MarketTrendPoint>): MarketTrendSummary {
        val volume = points.fold(0L) { total, point -> Math.addExact(total, point.volume) }
        val turnover = if (points.isNotEmpty() && points.all { it.average != null }) {
            points.fold(BigDecimal.ZERO) { total, point -> total + point.average!! * point.volume.toBigDecimal() }
        } else null
        return MarketTrendSummary(
            window, volume, turnover?.setScale(0, RoundingMode.HALF_UP),
            if (volume > 0) turnover?.divide(volume.toBigDecimal(), 2, RoundingMode.HALF_UP) else null,
            points.lastOrNull()?.median,
        )
    }
}
