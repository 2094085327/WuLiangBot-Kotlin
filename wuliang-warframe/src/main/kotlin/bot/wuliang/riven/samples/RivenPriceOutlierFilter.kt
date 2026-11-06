package bot.wuliang.riven.samples

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Component
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max

/** 同名属性按方向区分。 */
data class RivenMarketAttributeKey(val slug: String, val positive: Boolean)

data class RivenPriceFilterResult(
    val records: List<RivenSampleRecord>,
    val excludedCount: Int,
    val checkedGroupCount: Int,
)

/** 过滤同完整词条组内的极端报价。 */
@Component
class RivenPriceOutlierFilter(private val json: ObjectMapper) {
    fun keys(record: RivenSampleRecord): Set<RivenMarketAttributeKey>? = runCatching {
        val values = json.readTree(record.attributesJson)
        if (!values.isArray || values.size() !in 2..4) return null
        val slugs = mutableSetOf<String>()
        val keys = values.map { value ->
            val slug = value.path("slug").textValue()?.takeIf { it.isNotBlank() } ?: return null
            if (!slugs.add(slug) || !value.path("positive").isBoolean) return null
            RivenMarketAttributeKey(slug, value.path("positive").booleanValue())
        }.toSet()
        keys.takeIf { it.count { key -> key.positive } in 2..3 && it.count { key -> !key.positive } in 0..1 }
    }.getOrNull()

    fun filter(records: List<RivenSampleRecord>): RivenPriceFilterResult {
        val groups = records.filter { it.price > 0 }.mapNotNull { record ->
            keys(record)?.let { Triple(record.weaponSlug, it, record) }
        }.groupBy({ it.first to it.second }, { it.third })
        val excluded = mutableSetOf<RivenSampleRecord>()
        var checked = 0
        for (group in groups.values) {
            if (group.size < 8 || group.map { it.sellerId }.distinct().size < 3) continue
            checked++
            val logs = group.map { ln(it.price.toDouble()) }.sorted()
            val q1 = quantile(logs, .25)
            val q3 = quantile(logs, .75)
            // 至少保留八倍价格尺度的外侧空间，避免重复价导致IQR为零时误删普通报价。
            val margin = 3 * max(q3 - q1, ln(2.0))
            val retained = group.filter { ln(it.price.toDouble()) in (q1 - margin)..(q3 + margin) }
            if (retained.size < 5 || retained.map { it.sellerId }.distinct().size < 3) continue
            excluded.addAll(group - retained.toSet())
        }
        return RivenPriceFilterResult(records.filterNot { it in excluded }, excluded.size, checked)
    }

    private fun quantile(sorted: List<Double>, q: Double): Double {
        val index = (sorted.size - 1) * q
        val lower = floor(index).toInt()
        val upper = minOf(lower + 1, sorted.lastIndex)
        return sorted[lower] + (sorted[upper] - sorted[lower]) * (index - lower)
    }
}