package bot.wuliang.market.history

import bot.wuliang.config.WARFRAME_MARKET_ITEMS_STATISTICS
import bot.wuliang.entity.WfMarketItemEntity
import bot.wuliang.market.MarketApiClient
import bot.wuliang.market.MarketProperties
import bot.wuliang.market.MarketRequestPriority
import com.fasterxml.jackson.databind.JsonNode
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneOffset

/** 只读取statistics_closed.90days里的目标完整UTC日 */
@Component
class MarketDailyTradeSource(private val api: MarketApiClient, private val properties: MarketProperties) {
    fun fetch(item: WfMarketItemEntity, day: LocalDate): List<MarketDailyTradeAverage> {
        val slug = requireNotNull(item.urlName)
        require(slug.matches(Regex("[a-z0-9_]+")))
        val response = api.getJson(
            WARFRAME_MARKET_ITEMS_STATISTICS.format(slug),
            headers = mapOf("Platform" to properties.statistics.platform, "Language" to "en", "Crossplay" to "false"),
            priority = MarketRequestPriority.BACKGROUND,
        )
        return parse(response, requireNotNull(item.id), day, System.currentTimeMillis())
    }

    fun parse(json: JsonNode, itemId: String, day: LocalDate, fetchedAt: Long): List<MarketDailyTradeAverage> {
        require(day.isBefore(LocalDate.now(ZoneOffset.UTC))) { "只允许采集已结束的UTC日" }
        val daily = json.path("payload").path("statistics_closed").get("90days")
        require(daily != null && daily.isArray) { "WM成交统计缺少statistics_closed.90days数组" }
        val selected = daily.filter { row ->
            val timestamp = row["datetime"]?.takeIf(JsonNode::isTextual)?.asText()
                ?: error("WM每日成交统计缺少日期")
            val utc = OffsetDateTime.parse(timestamp).withOffsetSameInstant(ZoneOffset.UTC)
            utc.toLocalDate() == day && utc.toLocalTime() == LocalTime.MIDNIGHT
        }
        val records = selected.mapNotNull { row ->
            val type = row["order_type"]?.asText()
            require(type == null || type == "closed") { "每日成交统计中出现非closed记录" }
            val volume = row["volume"]
            require(volume != null && volume.isIntegralNumber && volume.canConvertToLong() && volume.longValue() >= 0) {
                "WM每日成交量无效"
            }
            if (volume.longValue() == 0L) return@mapNotNull null
            val average = row["wa_price"]?.takeUnless(JsonNode::isNull) ?: return@mapNotNull null
            require(average.isNumber && average.decimalValue().signum() > 0) { "WM每日成交加权均价无效" }
            val minimum = row["min_price"]
            val maximum = row["max_price"]
            require(
                minimum != null && minimum.isNumber && minimum.decimalValue().signum() > 0 &&
                        maximum != null && maximum.isNumber && maximum.decimalValue() >= minimum.decimalValue()
            ) {
                "WM每日成交最低价/最高价无效"
            }
            val variant = MarketTradeVariant.json(row)
            val rank = row["rank"]?.takeUnless(JsonNode::isNull)
                ?: row["modRank"]?.takeUnless(JsonNode::isNull) ?: row["mod_rank"]?.takeUnless(JsonNode::isNull)
            MarketDailyTradeAverage(
                itemId = itemId, platform = properties.statistics.platform, tradeDay = day,
                variantKey = MessageDigest.getInstance("SHA-256").digest(variant.toByteArray(Charsets.UTF_8))
                    .joinToString("") { "%02x".format(it) },
                variantJson = variant, rank = rank?.intValue() ?: -1,
                minPrice = minimum.decimalValue(), maxPrice = maximum.decimalValue(),
                averagePrice = average.decimalValue(), volume = volume.longValue(), fetchedAt = fetchedAt,
            )
        }
        require(records.distinctBy { it.variantKey }.size == records.size) { "同日同规格存在重复成交聚合" }
        return records
    }
}