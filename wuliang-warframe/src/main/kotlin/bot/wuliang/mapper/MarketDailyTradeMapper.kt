package bot.wuliang.mapper

import bot.wuliang.market.history.MarketDailyTradeAverage
import bot.wuliang.market.history.MarketRankingCriteria
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import java.time.LocalDate

@Mapper
interface MarketDailyTradeMapper {
    fun deleteItemDay(
        @Param("itemId") itemId: String,
        @Param("platform") platform: String,
        @Param("source") source: String,
        @Param("day") day: LocalDate
    )

    fun insertRecords(@Param("records") records: List<MarketDailyTradeAverage>)
    fun deleteExpired(
        @Param("platform") platform: String, @Param("source") source: String,
        @Param("targetDay") targetDay: LocalDate, @Param("previousDay") previousDay: LocalDate,
        @Param("limit") limit: Int
    ): Int

    fun selectPreviousDataDay(
        @Param("platform") platform: String, @Param("source") source: String,
        @Param("day") day: LocalDate
    ): LocalDate?

    fun countDay(
        @Param("platform") platform: String,
        @Param("source") source: String,
        @Param("day") day: LocalDate
    ): Long

    fun selectRanking(
        @Param("platform") platform: String, @Param("source") source: String, @Param("day") day: LocalDate,
        @Param("criteria") criteria: MarketRankingCriteria, @Param("offset") offset: Long, @Param("limit") limit: Int,
    ): List<MarketDailyTradeAverage>
}
