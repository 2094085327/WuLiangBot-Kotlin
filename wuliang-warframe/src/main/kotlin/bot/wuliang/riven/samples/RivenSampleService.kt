package bot.wuliang.riven.samples

import bot.wuliang.botLog.logUtil.LoggerUtils.logError
import bot.wuliang.mapper.RivenSampleMapper
import bot.wuliang.riven.RivenAttributeAliases
import bot.wuliang.riven.RivenAttributeCatalog
import bot.wuliang.riven.RivenAttributeMatch
import bot.wuliang.riven.analysis.RivenAnalysisReport
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

/** 为紫卡分析读取本地市场参考，并按样本缺口补采。 */
@Service
class RivenSampleService(
    private val source: RivenSampleSource,
    private val samples: RivenSampleMapper,
    private val policy: RivenSamplePolicy,
    private val properties: RivenSampleProperties,
    private val attributes: RivenAttributeCatalog,
    private val json: ObjectMapper,
    private val valuation: RivenMarketValuation,
) {
    /**
     * 为紫卡分析报告组装三类市场参考。
     *
     * 先读取三类参考。相似或完整同词条样本不足时共用正面查询，零洗不足时使用武器查询。
     * 每次最多补采一次，遵守共享日额度、查询间隔和锁内复查。
     * 完整匹配优先；最终仍无完整匹配时，才回退到仅正面相同。
     * 挂牌参考、相似挂牌估价和零洗参考分别计算。
     *
     * @param report 已完成理论分析的报告，提供武器、词条及可选等级。
     * @return 挂牌参考及独立的相似估价、零洗参考；功能关闭或词条未知时返回对应状态。
     * @throws InterruptedException 补查或计算期间线程被中断。
     */
    fun marketReference(report: RivenAnalysisReport): RivenMarketReference {
        if (!properties.enabled) return RivenMarketReference("DISABLED")
        val resolved = report.attributes.map {
            attributes.resolve(RivenAttributeAliases.slugForGameRef(it.tag) ?: it.name) as? RivenAttributeMatch.Found
        }
        if (resolved.any { it == null }) return RivenMarketReference(
            "UNKNOWN_ATTRIBUTES",
            zeroRollReference = zeroRollReference(report.weaponSlug),
            valuation = RivenMarketEstimate("UNKNOWN_ATTRIBUTES")
        )
        val query = RivenSampleQuery(
            positive = report.attributes.indices.filter { report.attributes[it].positive }
                .map { resolved[it]!!.definition.slug }.sorted(),
        )
        val required = json.writeValueAsString(report.attributes.indices.map {
            mapOf("slug" to resolved[it]!!.definition.slug, "positive" to report.attributes[it].positive)
        })
        val requiredPositive = json.writeValueAsString(query.positive.map {
            mapOf("slug" to it, "positive" to true)
        })

        /**
         * 读取指定等级和词条层级的本地有效挂牌。
         *
         * @param rank 紫卡等级；null表示不限等级。
         * @param positiveOnly true只要求正面集合相同，负面不限；false要求完整正负词条相同。
         * @return 通过有效性过滤的挂牌，以及本次使用的等级和词条匹配层级。
         */
        fun comparable(rank: Int?, positiveOnly: Boolean): MatchedSamples {
            val now = System.currentTimeMillis()
            val records = samples.comparable(
                report.weaponSlug,
                now - TimeUnit.HOURS.toMillis(minOf(properties.referenceMaxAgeHours, properties.observationMaxHours)),
                now - TimeUnit.DAYS.toMillis(properties.listingMaxDays),
                now - TimeUnit.DAYS.toMillis(properties.sellerMaxDays),
                if (positiveOnly) requiredPositive else required,
                if (positiveOnly) query.positive.size else report.attributes.size, rank, positiveOnly
            )
            return MatchedSamples(
                records.filter { policy.evaluate(it, now).eligible },
                rank, if (positiveOnly) "POSITIVE_ONLY" else "EXACT"
            )
        }

        /**
         * 优先匹配同等级挂牌，数量不足时尝试不限等级。
         *
         * 只有样本数增加时才回退；报告等级未知时直接不限等级。
         *
         * @param positiveOnly 是否只要求正面集合相同，含义同[comparable]。
         * @return 选定等级范围内的挂牌及匹配层级；不会发起WM请求。
         */
        fun findMatches(positiveOnly: Boolean = false): MatchedSamples {
            val sameRank = comparable(report.rank, positiveOnly)
            if (report.rank == null || enoughMatches(sameRank.records)) return sameRank
            // WM查询本身不限制等级。优先复用其它等级的同词条挂牌。
            val allRanks = comparable(null, positiveOnly)
            return if (allRanks.records.size > sameRank.records.size) allRanks else sameRank
        }

        val targets = report.attributes.indices.map {
            RivenMarketAttributeKey(resolved[it]!!.definition.slug, report.attributes[it].positive)
        }

        fun readReferences() = ReferenceSamples(
            findMatches(), marketEstimate(report.weaponSlug, targets), zeroRollReference(report.weaponSlug)
        )

        var current = readReferences()
        var origin = "LOCAL"
        var unavailable = false

        // 技术错误、未知词条不当作市场缺样本。两项词条参考共用同一个查询。
        fun canCollect() = current.estimate.status !in setOf("UNKNOWN_ATTRIBUTES", "UNAVAILABLE") &&
                current.zeroRoll.status != "UNAVAILABLE"

        val candidates = buildList {
            if (needsTerms(current)) add(query to true)
            if (insufficient(current.zeroRoll.status)) add(RivenSampleQuery() to false)
        }
        if (canCollect()) for ((candidate, terms) in candidates) {
            fun stillNeeded() = if (terms) needsTerms(current) else insufficient(current.zeroRoll.status)
            if (!stillNeeded()) continue
            try {
                val response = source.searchForAnalysis(report.weaponSlug, candidate) {
                    current = readReferences()
                    canCollect() && stillNeeded()
                }
                // 前一个查询在复用期内时，让零洗查询获得机会；不重复请求同一词条条件。
                if (response == null) continue
                origin = if (response.fetched) "WM" else "CACHE"
                current = readReferences()
                // 缓存复用或完成一次HTTP后结束；所有参考共用这次入库结果。
                break
            } catch (error: InterruptedException) {
                Thread.currentThread().interrupt(); throw error
            } catch (_: Exception) {
                unavailable = true
                // 入库成功但缓存或状态发布失败时，当前报告仍可使用已取得的数据。
                current = readReferences()
                break
            }
        }
        val matched = current.matched.takeIf { it.records.isNotEmpty() } ?: findMatches(positiveOnly = true)
        val zeroRoll = current.zeroRoll
        val estimate = current.estimate
        val records = matched.records
        val matchLevel = if (matched.rank == null) "ALL_RANKS" else "SAME_RANK"
        // 实际挂牌和相似估价各自返回，不互相覆盖状态。
        if (records.isEmpty()) return RivenMarketReference(
            if (unavailable) "UNAVAILABLE" else "NO_COMPARABLE",
            source = origin, matchLevel = matchLevel, zeroRollReference = zeroRoll, valuation = estimate
        )
        val prices = records.map { it.price }.sorted()
        return RivenMarketReference(
            if (enoughMatches(records)) "AVAILABLE" else "LIMITED", records.size,
            records.map { it.sellerId }.distinct().size, prices.first(), median(prices), prices.last(),
            records.minOf { it.lastObservedAt }, origin, matchLevel, zeroRoll,
            attributeMatchLevel = matched.attributeMatchLevel, valuation = estimate
        )
    }

    /**
     * 读取本地候选池，按方向词条相似度计算估价。
     *
     * 候选数量受[RivenSampleProperties.maximumReferenceSamples]限制。
     *
     * @param weapon WM武器标识。
     * @param targets 目标词条标识及各词条的正负方向。
     * @return 相似挂牌估价；普通读取或计算异常转换为UNAVAILABLE状态。
     * @throws InterruptedException 读取或计算期间线程被中断。
     */
    private fun marketEstimate(weapon: String, targets: List<RivenMarketAttributeKey>): RivenMarketEstimate = try {
        val now = System.currentTimeMillis()
        val records = samples.valuationReference(
            weapon,
            now - TimeUnit.HOURS.toMillis(minOf(properties.referenceMaxAgeHours, properties.observationMaxHours)),
            now - TimeUnit.DAYS.toMillis(properties.listingMaxDays),
            now - TimeUnit.DAYS.toMillis(properties.sellerMaxDays), properties.maximumReferenceSamples
        )
        valuation.estimate(records, targets, now)
    } catch (error: Exception) {
        if (error is InterruptedException) {
            Thread.currentThread().interrupt(); throw error
        }
        logError("紫卡相似挂牌估价暂不可用", error)
        RivenMarketEstimate("UNAVAILABLE")
    }

    private data class MatchedSamples(
        val records: List<RivenSampleRecord>, val rank: Int?,
        val attributeMatchLevel: String
    )

    private data class ReferenceSamples(
        val matched: MatchedSamples, val estimate: RivenMarketEstimate, val zeroRoll: RivenZeroRollReference
    )

    private fun insufficient(status: String) = status == "LIMITED" || status == "NO_COMPARABLE"

    private fun needsTerms(reference: ReferenceSamples) = !enoughMatches(reference.matched.records) ||
            insufficient(reference.estimate.status)

    /**
     * 判断挂牌参考或零洗参考是否达到数量门槛。
     *
     * @param records 已通过有效性过滤的挂牌。
     * @return 至少5张挂牌且来自至少3位不同卖家时为true。
     */
    private fun enoughMatches(records: List<RivenSampleRecord>) = records.size >= 5 &&
            records.map { it.sellerId }.distinct().size >= 3

    /**
     * 计算普通中位价，偶数条时取中间两价的平均值。
     *
     * @param sortedPrices 非空且按升序排列的价格。
     * @return 中位价，可能含小数。
     * @throws IndexOutOfBoundsException 价格列表为空。
     */
    private fun median(sortedPrices: List<Int>): Double {
        val middle = sortedPrices.size / 2
        return if (sortedPrices.size % 2 == 1) sortedPrices[middle].toDouble()
        else (sortedPrices[middle - 1].toDouble() + sortedPrices[middle]) / 2
    }

    /**
     * 统计同武器的本地有效零洗挂牌。
     *
     * @param weapon WM武器标识。
     * @return 零洗挂牌中位价及样本信息；无样本为NO_COMPARABLE，普通读取异常为UNAVAILABLE。
     * @throws InterruptedException 读取期间线程被中断。
     */
    private fun zeroRollReference(weapon: String): RivenZeroRollReference = try {
        val now = System.currentTimeMillis()
        val records = samples.zeroRollReference(
            weapon,
            now - TimeUnit.HOURS.toMillis(minOf(properties.referenceMaxAgeHours, properties.observationMaxHours)),
            now - TimeUnit.DAYS.toMillis(properties.listingMaxDays),
            now - TimeUnit.DAYS.toMillis(properties.sellerMaxDays), properties.maximumReferenceSamples
        )
        val eligible = records.filter { policy.evaluate(it, now).eligible }
        val sellers = eligible.map { it.sellerId }.distinct().size
        if (eligible.isEmpty()) RivenZeroRollReference("NO_COMPARABLE")
        else RivenZeroRollReference(
            if (enoughMatches(eligible)) "AVAILABLE" else "LIMITED", eligible.size, sellers,
            median(eligible.map { it.price }.sorted()), eligible.minOf { it.lastObservedAt })
    } catch (error: Exception) {
        if (error is InterruptedException) {
            Thread.currentThread().interrupt(); throw error
        }
        logError("紫卡零洗参考暂不可用", error)
        RivenZeroRollReference("UNAVAILABLE")
    }

}