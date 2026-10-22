package bot.wuliang.api

import bot.wuliang.botLog.logUtil.LoggerUtils.logError
import bot.wuliang.exception.RespBean
import bot.wuliang.market.presentation.MarketTrendReport
import bot.wuliang.market.presentation.MarketTrendRequest
import bot.wuliang.market.presentation.MarketTrendService
import bot.wuliang.market.presentation.MarketTrendUnavailableException
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Api(tags = ["Warframe接口"])
@RestController
@RequestMapping("/warframe/market")
class WarframeMarketHistoryController(
    private val trends: MarketTrendService,
) {
    @ApiOperation("按物品名称或别名获取同规格成交走势及两个窗口的摘要")
    @GetMapping("/trend")
    fun trend(
        @RequestParam item: String,
        @RequestParam(defaultValue = "90days") window: String,
        @RequestParam(required = false) rank: Int?,
        @RequestParam(defaultValue = "false") maxRank: Boolean,
    ): RespBean<MarketTrendReport> = try {
        RespBean.success(trends.query(MarketTrendRequest(item, window, rank, maxRank)))
    } catch (error: IllegalArgumentException) {
        RespBean.error(error.message ?: "走势查询参数无效。")
    } catch (error: MarketTrendUnavailableException) {
        RespBean.error(error.message.orEmpty())
    } catch (error: Exception) {
        if (error is InterruptedException) Thread.currentThread().interrupt()
        logError("WM走势查询失败：$item", error)
        RespBean.error("历史走势暂时不可用，请稍后重试。")
    }
}