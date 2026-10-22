package bot.wuliang.api

import bot.wuliang.exception.RespBean
import bot.wuliang.market.history.MarketRankingQuery
import bot.wuliang.market.history.MarketRankingRequest
import bot.wuliang.market.history.MarketRankingResult
import bot.wuliang.market.history.MarketRankingService
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** 查询已发布的完整 UTC 日成交统计，不在排行榜请求期间触发采集。 */
@Api(tags = ["Warframe接口"])
@RestController
@RequestMapping("/warframe/market")
class WarframeMarketRankingController(private val rankings: MarketRankingService) {
    @ApiOperation("WM排行榜支持完整UTC日成交均价、最低价、最高价和成交量排序，同步期间沿用旧日")
    @GetMapping("/ranking")
    fun ranking(
        @RequestParam(required = false) category: String?,
        @RequestParam(defaultValue = "TRADE_AVERAGE") metric: String,
        @RequestParam(required = false) rankSelection: String?,
        @RequestParam(required = false) exactRank: Int?,
        @RequestParam(defaultValue = "desc") sort: String,
        @RequestParam(defaultValue = "1") page: Int,
        @RequestParam(defaultValue = "20") pageSize: Int,
        @RequestParam(defaultValue = "false") full: Boolean,
    ): RespBean<MarketRankingResult> {
        val request = try {
            require(sort.lowercase() in setOf("asc", "desc")) { "sort 仅支持 asc 或 desc。" }
            val selectedCategory = category?.let(MarketRankingQuery::category)
            MarketRankingRequest(
                category = selectedCategory,
                metric = MarketRankingQuery.metric(metric),
                rankSelection = rankSelection?.let(MarketRankingQuery::rankSelection),
                exactRank = exactRank, descending = sort.equals("desc", true), page = page, pageSize = pageSize,
                fullCategory = full && selectedCategory != null,
            )
        } catch (e: IllegalArgumentException) {
            return RespBean.error(e.message ?: "排行榜参数无效")
        }
        return try {
            RespBean.success(rankings.query(request))
        } catch (e: IllegalArgumentException) {
            RespBean.error(e.message ?: "排行榜参数无效")
        }
    }
}