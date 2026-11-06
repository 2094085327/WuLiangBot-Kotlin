package bot.wuliang.riven.samples

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component
import java.time.OffsetDateTime
import java.util.concurrent.TimeUnit

/** 采集使用严格的原始字段。 */
@Component
class RivenSampleDecoder(
    private val json: ObjectMapper,
) {
    /**
     * 将一次 WM 紫卡搜索响应解码为样本批次，并汇总逐条拒收原因。
     *
     * 按挂牌 ID 去重，保留首条成功解码的记录。被拒收记录中格式正确的 ID 列为待失效候选，
     * 但同批次中已有合格记录的 ID 不列入待失效名单。
     *
     * @param response 包含 `payload.auctions` 数组的 WM 搜索响应。
     * @param weapon 本次查询的 WM 武器标识，用于校验挂牌所属武器。
     * @param now 本次实际观察时间，单位为毫秒时间戳，用于时间校验和记录观察时间。
     * @return 原始条数、去重后的样本、待失效挂牌 ID，以及各拒收原因的条数。
     * @throws IllegalStateException 响应中的 `payload.auctions` 缺失或不是数组。
     */
    fun decode(response: JsonNode, weapon: String, now: Long): RivenSampleBatch {
        val rows = response.path("payload").path("auctions")
        check(rows.isArray) { "紫卡搜索响应缺少 auctions 数组" }
        val records = linkedMapOf<String, RivenSampleRecord>()
        val skipped = mutableMapOf<String, Int>()
        val invalidated = mutableSetOf<String>()
        for (row in rows) {
            try {
                val record = record(row, weapon, now)
                if (records.putIfAbsent(record.auctionId, record) != null) {
                    skipped.merge("duplicate_id", 1, Int::plus)
                }
            } catch (error: InvalidSample) {
                skipped.merge(error.reason, 1, Int::plus)
                // 只失效已经收录的挂牌。
                row.path("id").textValue()?.takeIf { OBJECT_ID.matches(it) }?.let(invalidated::add)
            }
        }
        return RivenSampleBatch(rows.size(), records.values.toList(), (invalidated - records.keys).toList(), skipped)
    }

    /**
     * 校验单条直接出售挂牌，并生成保留原始卡面数据的样本记录。
     *
     * 要求起拍价与一口价相同，挂牌属于指定武器，属性标识不重复，且包含两条或三条正面、
     * 至多一条负面词条。创建时间、更新时间和已知的卖家活跃时间均不得超前超过五分钟。
     * 词条按标识排序，数值移除多余的末尾零后序列化；原始 `item` 单独保存。
     * 挂牌状态只决定记录的 `active`，样本时效与卖家活跃度由 [RivenSamplePolicy.evaluate] 判断。
     *
     * @param row 搜索响应中的一条原始挂牌。
     * @param weapon 预期的 WM 武器标识。
     * @param now 本次实际观察时间，单位为毫秒时间戳，同时填入首次和最近观察时间。
     * @return 通过字段与组合校验的样本；挂牌当前不可用时，其 `active` 为 false。
     * @throws InvalidSample 挂牌不是直接出售，或必需字段、词条组合、时间等不符合规则。
     */
    private fun record(row: JsonNode, weapon: String, now: Long): RivenSampleRecord {
        if (!row.path("is_direct_sell").isBoolean || !row.path("is_direct_sell").booleanValue()) reject("auction")
        val price = integer(row, "buyout_price", 1..Int.MAX_VALUE)
        if (integer(row, "starting_price", 1..Int.MAX_VALUE) != price) reject("inconsistent_direct_price")
        val id = text(row, "id").also { if (!OBJECT_ID.matches(it)) reject("invalid_id") }
        val item = row.path("item")
        if (item.path("type").asText() != "riven" || item.path("weapon_url_name")
                .asText() != weapon
        ) reject("wrong_weapon")
        val seller = text(row.path("owner"), "id").also { if (!OBJECT_ID.matches(it)) reject("invalid_seller") }
        val rank = integer(item, "mod_rank", 0..8)
        val rerolls = integer(item, "re_rolls", 0..Int.MAX_VALUE)
        val mastery = integer(item, "mastery_level", 0..40)
        val polarity = text(item, "polarity")
        val attributes = item.path("attributes")
        if (!attributes.isArray || attributes.size() !in 2..4) reject("invalid_attributes")
        val slugs = mutableSetOf<String>()
        val canonical = attributes.map { attribute ->
            val slug = text(attribute, "url_name")
            if (!slugs.add(slug) || !attribute.path("positive").isBoolean) reject("invalid_attributes")
            val value = attribute.path("value")
            if (!value.isNumber || !value.doubleValue()
                    .isFinite() || kotlin.math.abs(value.doubleValue()) > 1_000_000
            ) {
                reject("invalid_attributes")
            }
            sortedMapOf<String, Any>(
                "slug" to slug, "positive" to attribute.path("positive").booleanValue(),
                "value" to value.decimalValue().stripTrailingZeros()
            )
        }.sortedBy { it.getValue("slug").toString() }
        val positives = canonical.count { it["positive"] == true }
        if (positives !in 2..3 || canonical.size - positives !in 0..1) reject("invalid_combination")
        val created = timestamp(row, "created") ?: reject("missing_created")
        val updated = timestamp(row, "updated") ?: reject("missing_updated")
        val lastSeen = timestamp(row.path("owner"), "last_seen")
        if (created > now + TimeUnit.MINUTES.toMillis(5) || updated > now + TimeUnit.MINUTES.toMillis(5) ||
            lastSeen != null && lastSeen > now + TimeUnit.MINUTES.toMillis(5)
        ) reject("future_timestamp")
        val active = row.path("visible").asBoolean(false) && row.path("closed").isBoolean &&
                !row.path("closed").booleanValue() && row.path("private").isBoolean &&
                !row.path("private").booleanValue() && row.path("winner").isNull && row.path("is_marked_for").isNull
        return RivenSampleRecord(
            id, weapon, seller,
            price, rank, rerolls, mastery, polarity,
            json.writeValueAsString(canonical), json.writeValueAsString(item), created, updated, lastSeen,
            row.path("owner").path("status").asText("unknown"), active, now, now
        )
    }

    /**
     * 读取非空白且长度不超过128个字符的原始字符串
     *
     * @param node 字段所属的 JSON 节点。
     * @param key 要读取的字段名。
     * @return 校验通过的原始字符串。
     * @throws InvalidSample 字段缺失、不是字符串、为空白或过长，原因代码为 `missing_字段名`。
     */
    private fun text(node: JsonNode, key: String): String = node.path(key).textValue()?.takeIf {
        it.isNotBlank() && it.length <= 128
    } ?: reject("missing_$key")

    /**
     * 读取指定范围内的 JSON 整数
     *
     * @param node 字段所属的 JSON 节点。
     * @param key 要读取的字段名。
     * @param range 允许的整数范围，包含两端。
     * @return 可由 Int 表示且位于指定范围内的整数。
     * @throws InvalidSample 字段缺失、类型不符、超出 Int 或指定范围，原因代码为 `invalid_字段名`。
     */
    private fun integer(node: JsonNode, key: String, range: IntRange): Int {
        val value = node.path(key)
        if (!value.isIntegralNumber || !value.canConvertToInt() || value.intValue() !in range) reject("invalid_$key")
        return value.intValue()
    }

    /**
     * 读取带时区偏移的日期时间，并转换为毫秒时间戳。
     *
     * 本方法允许字段缺失，由调用方决定该时间是否必需；无法解析的已有值直接拒收。
     *
     * @param node 字段所属的 JSON 节点。
     * @param key 要读取的时间字段名。
     * @return 对应时刻的毫秒时间戳；字段缺失或为 JSON null 时返回 null。
     * @throws InvalidSample 字段值无法按 OffsetDateTime 解析，原因代码为 `invalid_字段名`。
     */
    private fun timestamp(node: JsonNode, key: String): Long? {
        val value = node.path(key)
        if (value.isMissingNode || value.isNull) return null
        return try {
            OffsetDateTime.parse(value.asText()).toInstant().toEpochMilli()
        } catch (_: java.time.format.DateTimeParseException) {
            reject("invalid_$key")
        }
    }

    /**
     * 以指定原因中止当前挂牌的解码，由 [decode] 统计拒收原因并继续处理其他挂牌。
     *
     * @param reason 用于批次统计的拒收原因代码。
     * @throws InvalidSample 始终抛出携带该原因的异常，不返回结果。
     */
    private fun reject(reason: String): Nothing = throw InvalidSample(reason)
    private class InvalidSample(val reason: String) : RuntimeException(reason)

    companion object {
        private val OBJECT_ID = Regex("[0-9a-f]{24}")
    }
}

@Component
class RivenSamplePolicy(private val properties: RivenSampleProperties) {
    /**
     * 根据挂牌状态、观察时间、挂牌时长和卖家活跃度，判断样本是否可用并计算权重。
     *
     * 挂牌失效、观察过期、挂牌时长或距卖家最近活跃的时长超过硬上限时，样本不可用且权重为0。
     * 挂牌时长和距卖家最近活跃的时长分别超过软上限时，各将权重乘以0.5。
     * 缺少卖家活跃时间时只附加提示，并将权重乘以0.25，不单独导致样本不可用。
     *
     * @param record 待判断的本地挂牌样本。
     * @param now 本次判断使用的当前时间，单位为毫秒时间戳。
     * @return 样本可用性、叠加折减后的权重，以及无效原因或提示代码。
     */
    fun evaluate(record: RivenSampleRecord, now: Long): RivenSampleEligibility {
        val reasons = mutableListOf<String>()
        var weight = 1.0
        if (!record.active) reasons += "inactive_listing"
        if (now - record.lastObservedAt > TimeUnit.HOURS.toMillis(properties.observationMaxHours)) reasons += "stale_observation"
        val age = now - record.createdAt
        if (age > TimeUnit.DAYS.toMillis(properties.listingMaxDays)) reasons += "old_listing"
        else if (age > TimeUnit.DAYS.toMillis(properties.listingSoftDays)) weight *= .5
        val sellerAge = record.sellerLastSeen?.let { now - it }
        if (sellerAge == null) weight *= .25
        else if (sellerAge > TimeUnit.DAYS.toMillis(properties.sellerMaxDays)) reasons += "inactive_seller"
        else if (sellerAge > TimeUnit.DAYS.toMillis(properties.sellerSoftDays)) weight *= .5
        if (sellerAge == null) reasons += "unknown_seller_activity"
        val eligible = reasons.none { it != "unknown_seller_activity" }
        return RivenSampleEligibility(eligible, if (eligible) weight else 0.0, reasons)
    }
}