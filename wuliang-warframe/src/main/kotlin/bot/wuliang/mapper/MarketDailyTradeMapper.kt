package bot.wuliang.mapper

import bot.wuliang.market.history.MarketDailyTradeAverage
import bot.wuliang.market.history.MarketRankingCriteria
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import java.time.LocalDate

@Mapper
interface MarketDailyTradeMapper {
    fun deleteStaleDay(
        @Param("day") day: LocalDate,
        @Param("fetchedAt") fetchedAt: Long,
    )

    fun insertRecords(@Param("records") records: List<MarketDailyTradeAverage>)
    fun deleteExpired(
        @Param("targetDay") targetDay: LocalDate, @Param("previousDay") previousDay: LocalDate,
    ): Int

    fun countImportedRecords(
        @Param("day") day: LocalDate,
        @Param("fetchedAt") fetchedAt: Long,
    ): Long

    fun selectRanking(
        @Param("day") day: LocalDate,
        @Param("criteria") criteria: MarketRankingCriteria, @Param("offset") offset: Long, @Param("limit") limit: Int,
    ): List<MarketDailyTradeAverage>
}
