package bot.wuliang.riven.samples

import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/** 同武器的方向词条相似度加权挂牌参考。 */
@Component
class RivenMarketValuation(
    private val properties: RivenSampleProperties,
    private val policy: RivenSamplePolicy,
    private val outliers: RivenPriceOutlierFilter,
) {
    private data class Comparable(val record: RivenSampleRecord, val similarity: Double) {
        val weight get() = similarity * similarity
    }

    /**
     * 根据同武器挂牌与目标方向词条的相似度，计算挂牌参考价和分位区间。
     *
     * 目标词条须包含两条或三条正面、至多一条负面，同一属性标识只能出现一次。
     * 按观察时间、正价、卖家状态和有效性策略筛选样本，截取配置上限后过滤异常报价。
     * 观察窗口取参考有效期与样本有效期的较小值。
     * 相似度为属性标识与正负方向集合的交集大小除以并集大小，词条数值不参与计算。
     *
     * @param reference 调用方提供的同一武器挂牌样本，按输入顺序截取有效候选上限。
     * @param targets 已解析的目标属性标识及正负方向。
     * @param now 本次有效性判断使用的当前时间，单位为毫秒时间戳。
     * @return 挂牌估价及统计信息；目标词条重复或正负数量不合法时返回 `UNKNOWN_ATTRIBUTES`。
     */
    fun estimate(
        reference: List<RivenSampleRecord>,
        targets: List<RivenMarketAttributeKey>,
        now: Long
    ): RivenMarketEstimate {
        val target = targets.toSet()
        if (target.size != targets.size || targets.map { it.slug }.distinct().size != targets.size ||
            target.count { it.positive } !in 2..3 || target.count { !it.positive } !in 0..1
        ) {
            return RivenMarketEstimate("UNKNOWN_ATTRIBUTES")
        }
        val observedAfter =
            now - TimeUnit.HOURS.toMillis(minOf(properties.referenceMaxAgeHours, properties.observationMaxHours))
        val candidates = reference.asSequence()
            .filter {
                it.lastObservedAt >= observedAfter && it.price > 0 &&
                        it.sellerStatus in setOf("online", "ingame") && policy.evaluate(it, now).eligible
            }
            .take(properties.maximumReferenceSamples).toList()
        val cleaned = outliers.filter(candidates)
        val scored = cleaned.records.mapNotNull { record ->
            val keys = outliers.keys(record) ?: return@mapNotNull null
            val intersection = keys.count { it in target }
            Comparable(record, intersection.toDouble() / (keys.size + target.size - intersection))
        }
        return estimate(scored, cleaned)
    }

    /**
     * 选择相似样本池，计算加权分位报价并按样本和卖家数量评定可信度。
     *
     * 优先选择相似度至少50%的样本，不足三条时将门槛放宽到25%。未放宽时，
     * 至少五条相似度达到75%的样本且覆盖三位卖家为高可信度；未放宽且候选池至少三条、
     * 覆盖三位卖家为中可信度，其余为低可信度。
     *
     * 报价使用相似度平方加权的50%分位数，区间取25%至75%分位数。
     * 观察时间取参与估价样本中最早的 `lastObservedAt`，清洗统计沿用原候选池的结果。
     *
     * @param scored 已过滤异常报价并计算方向词条相似度的候选样本。
     * @param cleaned 当前候选池的报价过滤结果，用于携带剔除数和已检查组数。
     * @return 无可比样本时返回 `NO_COMPARABLE`，低可信度时返回 `LIMITED`，其余返回 `AVAILABLE`。
     */
    private fun estimate(scored: List<Comparable>, cleaned: RivenPriceFilterResult): RivenMarketEstimate {
        var pool = scored.filter { it.similarity >= .5 }
        val relaxed = pool.size < 3
        if (relaxed) pool = scored.filter { it.similarity >= .25 }
        if (pool.isEmpty()) return RivenMarketEstimate(
            "NO_COMPARABLE",
            excludedOutlierCount = cleaned.excludedCount, outlierCheckedGroupCount = cleaned.checkedGroupCount
        )
        val sellers = pool.map { it.record.sellerId }.distinct().size
        val close = pool.filter { it.similarity >= .75 }
        val confidence = when {
            !relaxed && close.size >= 5 && close.map { it.record.sellerId }.distinct().size >= 3 -> "HIGH"
            !relaxed && pool.size >= 3 && sellers >= 3 -> "MEDIUM"
            else -> "LOW"
        }
        val prices = pool.sortedBy { it.record.price }
        /**
         * 在外层非空且按价格升序排列的样本中，查找加权经验分位价。
         *
         * 每条样本的权重为相似度的平方。取累计权重首次达到总权重乘以 [q] 的挂牌价格，
         * 不做价格插值；浮点累计未命中阈值时返回最高价。
         *
         * @param q 取值范围为0至1的分位点，当前用于计算25%、50%和75%分位价。
         * @return 对应样本的整数挂牌价格，单位为白金P。
         */
        fun quantile(q: Double): Int {
            val threshold = prices.sumOf { it.weight } * q
            var cumulative = 0.0
            for (row in prices) {
                cumulative += row.weight
                if (cumulative >= threshold) return row.record.price
            }
            return prices.last().record.price
        }
        return RivenMarketEstimate(
            if (confidence == "LOW") "LIMITED" else "AVAILABLE",
            quantile(.5), quantile(.25), quantile(.75), confidence, pool.size, sellers, close.size,
            relaxed, (pool.minOf { it.similarity } * 100).roundToInt(), pool.minOf { it.record.lastObservedAt },
            cleaned.excludedCount, cleaned.checkedGroupCount
        )
    }

}