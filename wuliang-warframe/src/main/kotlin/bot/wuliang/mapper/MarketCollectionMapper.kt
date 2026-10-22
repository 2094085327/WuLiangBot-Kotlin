package bot.wuliang.mapper

import bot.wuliang.market.history.MarketCollectionProgress
import bot.wuliang.market.history.MarketPublicationCheckpoint
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import java.time.LocalDate

/** 逐物品采集进度，复用原同步表以保留断点；不访问旧历史统计明细。 */
@Mapper
interface MarketCollectionMapper {
    fun prepareDay(
        @Param("itemIds") itemIds: List<String>, @Param("platform") platform: String,
        @Param("source") source: String, @Param("day") day: LocalDate
    )

    fun findDueDailyItemIds(
        @Param("platform") platform: String,
        @Param("source") source: String,
        @Param("day") day: LocalDate,
        @Param("maxAttempts") maxAttempts: Int,
    ): List<String>

    fun selectProgress(
        @Param("platform") platform: String, @Param("source") source: String,
        @Param("day") day: LocalDate, @Param("maxAttempts") maxAttempts: Int
    ): MarketCollectionProgress

    fun checkpointPublication(
        @Param("platform") platform: String, @Param("source") source: String,
        @Param("day") day: LocalDate
    )

    fun selectPublicationCheckpoint(
        @Param("platform") platform: String,
        @Param("source") source: String
    ): MarketPublicationCheckpoint?

    fun markDataSuccess(
        @Param("itemId") itemId: String,
        @Param("platform") platform: String,
        @Param("source") source: String,
        @Param("now") now: Long,
        @Param("day") day: LocalDate
    )

    fun markAttempt(
        @Param("itemId") itemId: String,
        @Param("platform") platform: String,
        @Param("source") source: String,
        @Param("now") now: Long,
        @Param("day") day: LocalDate,
        @Param("maxAttempts") maxAttempts: Int,
    ): Int

    fun markFailure(
        @Param("itemId") itemId: String,
        @Param("platform") platform: String,
        @Param("source") source: String,
        @Param("now") now: Long,
        @Param("error") error: String
    )
}