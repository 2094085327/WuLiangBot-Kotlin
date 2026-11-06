package bot.wuliang.riven.samples

import bot.wuliang.mapper.RivenSampleMapper
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.concurrent.TimeUnit

/** 只保存已取得的响应；不生成目录任务、轮次或补采计划。 */
@Service
class RivenSampleStore(
    private val mapper: RivenSampleMapper,
    private val json: ObjectMapper,
    private val decoder: RivenSampleDecoder,
    private val properties: RivenSampleProperties,
) {
    fun observation(key: String) = mapper.observation(key)

    /** 只读预热去重所需字段，每批最多 50 个条件 */
    fun warmupObservations(keys: List<String>): Map<String, RivenQueryObservation> {
        require(keys.size <= 50)
        return if (keys.isEmpty()) emptyMap() else mapper.warmupObservations(keys)
            .associateBy { it.queryKey }
    }

    @Transactional
    fun record(
        weapon: String, key: String, query: RivenSampleQuery,
        source: String, response: JsonNode, observedAt: Long
    ) {
        if ((observation(key)?.lastSuccessAt ?: 0) >= observedAt) return
        val batch = decoder.decode(response, weapon, observedAt)
        if (batch.records.isNotEmpty()) {
            val previous = mapper.existing(batch.records.map { it.auctionId }.sorted())
                .associateBy { it.auctionId }
            val fresh = batch.records.filter { (previous[it.auctionId]?.lastObservedAt ?: 0) <= observedAt }
            if (fresh.isNotEmpty()) mapper.upsert(fresh)
        }
        if (batch.invalidatedIds.isNotEmpty()) {
            val invalidations = mapper.existing(batch.invalidatedIds.sorted())
                .filter { it.weaponSlug == weapon && it.active && it.lastObservedAt <= observedAt }
                .map { it.auctionId }
            if (invalidations.isNotEmpty()) {
                mapper.invalidate(weapon, invalidations, observedAt)
            }
        }
        val queryJson = json.writeValueAsString(query)
        val skipped = json.writeValueAsString(batch.skipped)
        mapper.success(
            RivenQueryObservation(
                key, weapon, queryJson, source,
                observedAt, observedAt, batch.rawCount, batch.records.size, skipped
            )
        )
    }

    fun failure(weapon: String, key: String, query: RivenSampleQuery, source: String, now: Long, message: String) {
        mapper.failure(
            RivenQueryObservation(
                queryKey = key,
                weaponSlug = weapon, queryJson = json.writeValueAsString(query), source = source,
                lastAttemptAt = now, lastError = message.take(1000),
                retryAfter = now + TimeUnit.MINUTES.toMillis(properties.failureCooldownMinutes)
            )
        )
    }

    fun prune(now: Long) {
        val cutoff = now - TimeUnit.DAYS.toMillis(properties.retentionDays)
        mapper.pruneQueries(cutoff)
        mapper.pruneSamples(cutoff)
    }
}