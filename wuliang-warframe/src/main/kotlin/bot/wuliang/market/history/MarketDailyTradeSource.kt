package bot.wuliang.market.history

import bot.wuliang.httpUtil.HttpUtil
import bot.wuliang.market.MarketProperties
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.JsonToken
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import okhttp3.OkHttpClient
import okhttp3.Request
import org.springframework.stereotype.Component
import java.io.IOException
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.*
import java.util.concurrent.TimeUnit
import javax.annotation.PreDestroy

/** 单次成功下载的统计；fetchedAt 同时标识本次导入，用于清理旧重试残留。 */
data class MarketDailyTradeImport(
    val fetchedAt: Long,
    val itemCount: Long,
    val recordCount: Long,
    val unmatchedItems: Int,
)

/** 流式读取 HTTP 响应，每 500 条交给独立事务，不保留完整文件。 */
@Component
class MarketDailyTradeSource(private val properties: MarketProperties, private val objectMapper: ObjectMapper) {
    private val hexFormat = HexFormat.of()
    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(properties.statistics.historyRequestTimeoutSeconds, TimeUnit.SECONDS)
        .callTimeout(properties.statistics.historyRequestTimeoutSeconds, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .followRedirects(false)
        .build()

    fun fetch(
        day: LocalDate,
        catalogIds: Set<String>,
        saveBatch: (List<MarketDailyTradeAverage>) -> Unit,
    ): MarketDailyTradeImport {
        require(day.isBefore(LocalDate.now(ZoneOffset.UTC))) { "只允许采集已结束的UTC日" }
        val request = Request.Builder()
            .url("${properties.statistics.historyBaseUrl}price_history_$day.json")
            .header("Accept", "application/json")
            .header("User-Agent", properties.userAgent)
            .build()
        for (attempt in 1..properties.maxAttempts) {
            try {
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val fetchedAt = System.currentTimeMillis()
                        return objectMapper.factory.createParser(response.body.byteStream()).use { parser ->
                            parse(parser, catalogIds, day, fetchedAt, saveBatch)
                        }
                    }
                    if (attempt == properties.maxAttempts || response.code !in setOf(500, 502, 503, 504)) {
                        throw HttpUtil.HttpException(response.code, response.peekBody(1000).string())
                    }
                }
            } catch (error: IOException) {
                if (Thread.currentThread().isInterrupted || attempt == properties.maxAttempts) throw error
            }
            Thread.sleep(1_000L shl (attempt - 1))
        }
        error("每日归档请求未返回结果")
    }

    private fun parse(
        parser: JsonParser,
        catalogIds: Set<String>,
        day: LocalDate,
        fetchedAt: Long,
        saveBatch: (List<MarketDailyTradeAverage>) -> Unit,
    ): MarketDailyTradeImport {
        require(parser.nextToken() == JsonToken.START_OBJECT) { "归档格式无效" }
        val archiveIds = HashSet<String>()
        val batch = ArrayList<MarketDailyTradeAverage>(BATCH_SIZE)
        val digest = MessageDigest.getInstance("SHA-256")
        var itemCount = 0L
        var recordCount = 0L
        var unmatchedItems = 0
        while (parser.nextToken() != JsonToken.END_OBJECT) {
            require(parser.currentToken == JsonToken.FIELD_NAME) { "物品名称无效" }
            val name = parser.currentName
            require(parser.nextToken() == JsonToken.START_ARRAY) { "物品记录不是数组：$name" }
            var itemId: String? = null
            var traded = false
            val variants = HashSet<String>()
            while (parser.nextToken() != JsonToken.END_ARRAY) {
                require(parser.currentToken == JsonToken.START_OBJECT) { "物品记录无效：$name" }
                // 只为当前这一条记录构建 JsonNode，处理后即可回收。
                val row: JsonNode = objectMapper.readTree(parser)
                val id = row["item_id"]?.takeIf(JsonNode::isTextual)?.asText()
                require(!id.isNullOrBlank()) { "记录缺少item_id：$name" }
                if (itemId == null) {
                    require(archiveIds.add(id)) { "归档中item_id重复：$id" }
                    itemId = id
                } else require(itemId == id) { "同一物品名称对应多个item_id：$name" }
                val timestamp = row["datetime"]?.takeIf(JsonNode::isTextual)?.asText()
                    ?: error("每日统计缺少日期：$name")
                val utc = OffsetDateTime.parse(timestamp).withOffsetSameInstant(ZoneOffset.UTC)
                require(utc.toLocalDate() == day && utc.toLocalTime() == LocalTime.MIDNIGHT) {
                    "记录不属于目标完整UTC日：$name / $timestamp"
                }
                val type = row["order_type"]?.asText()
                require(type == "closed" || type == "buy" || type == "sell") { "订单类型无效：$name / $type" }
                if (type != "closed") continue
                val record = parseClosed(row, id, day, fetchedAt, digest) ?: continue
                require(variants.add(record.variantKey)) { "同日同规格存在重复成交聚合：$id" }
                if (id !in catalogIds) continue
                traded = true
                recordCount++
                batch.add(record)
                if (batch.size == BATCH_SIZE) {
                    saveBatch(batch)
                    batch.clear()
                }
            }
            require(itemId != null) { "物品记录为空数组：$name" }
            if (itemId !in catalogIds) unmatchedItems++
            if (traded) itemCount++
        }
        require(parser.nextToken() == null) { "归档存在多余内容" }
        require(recordCount > 0) { "归档与WM目录没有匹配的有效成交，保留旧榜" }
        if (batch.isNotEmpty()) saveBatch(batch)
        return MarketDailyTradeImport(fetchedAt, itemCount, recordCount, unmatchedItems)
    }

    private fun parseClosed(
        row: JsonNode, itemId: String, day: LocalDate, fetchedAt: Long, digest: MessageDigest,
    ): MarketDailyTradeAverage? {
        val volume = row["volume"]
        require(volume != null && volume.isIntegralNumber && volume.canConvertToLong() && volume.longValue() >= 0) {
            "每日成交量无效：$itemId"
        }
        if (volume.longValue() == 0L) return null
        val average = row["wa_price"]?.takeUnless(JsonNode::isNull) ?: return null
        require(average.isNumber && average.decimalValue().signum() > 0) { "每日成交加权均价无效：$itemId" }
        val minimum = row["min_price"]
        val maximum = row["max_price"]
        require(
            minimum != null && minimum.isNumber && minimum.decimalValue().signum() > 0 &&
                    maximum != null && maximum.isNumber && maximum.decimalValue() >= minimum.decimalValue()
        ) { "每日成交最低价/最高价无效：$itemId" }
        val variant = MarketTradeVariant.json(row)
        val rank = row["rank"]?.takeUnless(JsonNode::isNull)
            ?: row["modRank"]?.takeUnless(JsonNode::isNull) ?: row["mod_rank"]?.takeUnless(JsonNode::isNull)
        return MarketDailyTradeAverage(
            itemId = itemId, tradeDay = day,
            variantKey = hexFormat.formatHex(digest.digest(variant.toByteArray(Charsets.UTF_8))),
            variantJson = variant, rank = rank?.intValue() ?: -1,
            minPrice = minimum.decimalValue(), maxPrice = maximum.decimalValue(),
            averagePrice = average.decimalValue(), volume = volume.longValue(), fetchedAt = fetchedAt,
        )
    }

    @PreDestroy
    fun close() {
        client.dispatcher.executorService.shutdownNow()
        client.connectionPool.evictAll()
    }

    companion object {
        const val BATCH_SIZE = 500
    }
}