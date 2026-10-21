package bot.wuliang.api

import bot.wuliang.botLog.logUtil.LoggerUtils.logError
import bot.wuliang.exception.RespBean
import bot.wuliang.market.history.MarketHistoryResult
import bot.wuliang.market.history.MarketHistoryService
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Api(tags = ["Warframe接口"])
@RestController
@RequestMapping("/warframe/market")
class WarframeMarketHistoryController(private val history: MarketHistoryService) {
    @ApiOperation("按需获取并缓存 WM closed 历史，保留各等级/镶星规格")
    @GetMapping("/history")
    fun history(
        @RequestParam slug: String,
        @RequestParam(defaultValue = "90days") window: String,
    ): RespBean<MarketHistoryResult> = try {
        RespBean.success(history.query(slug, window))
    } catch (error: IllegalArgumentException) {
        RespBean.error(error.message ?: "历史查询参数或上游响应无效")
    } catch (error: Exception) {
        if (error is InterruptedException) Thread.currentThread().interrupt()
        logError("WM历史查询失败：$slug", error)
        RespBean.error("历史趋势暂时不可用，请稍后重试。")
    }
}