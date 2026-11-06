package bot.wuliang.mapper

import bot.wuliang.riven.samples.RivenQueryObservation
import bot.wuliang.riven.samples.RivenSampleRecord
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param

@Mapper
interface RivenSampleMapper {
    fun observation(@Param("key") key: String): RivenQueryObservation?
    fun warmupObservations(@Param("keys") keys: List<String>): List<RivenQueryObservation>
    fun success(@Param("o") observation: RivenQueryObservation)
    fun failure(@Param("o") observation: RivenQueryObservation)
    fun existing(@Param("ids") ids: List<String>): List<RivenSampleRecord>
    fun upsert(@Param("records") records: List<RivenSampleRecord>)
    fun invalidate(
        @Param("weapon") weapon: String,
        @Param("ids") ids: List<String>, @Param("now") now: Long
    )

    fun comparable(
        @Param("weapon") weapon: String,
        @Param("observedAfter") observedAfter: Long, @Param("createdAfter") createdAfter: Long,
        @Param("sellerAfter") sellerAfter: Long, @Param("attributes") attributes: String,
        @Param("attributeCount") attributeCount: Int, @Param("rank") rank: Int?,
        @Param("positiveOnly") positiveOnly: Boolean
    ): List<RivenSampleRecord>

    fun zeroRollReference(
        @Param("weapon") weapon: String,
        @Param("observedAfter") observedAfter: Long, @Param("createdAfter") createdAfter: Long,
        @Param("sellerAfter") sellerAfter: Long, @Param("limit") limit: Int
    ): List<RivenSampleRecord>

    fun valuationReference(
        @Param("weapon") weapon: String,
        @Param("observedAfter") observedAfter: Long, @Param("createdAfter") createdAfter: Long,
        @Param("sellerAfter") sellerAfter: Long, @Param("limit") limit: Int
    ): List<RivenSampleRecord>

    fun pruneQueries(@Param("cutoff") cutoff: Long): Int
    fun pruneSamples(@Param("cutoff") cutoff: Long): Int
}