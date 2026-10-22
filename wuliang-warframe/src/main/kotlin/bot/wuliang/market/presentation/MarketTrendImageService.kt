package bot.wuliang.market.presentation

import bot.wuliang.imageProcess.WebImgUtil
import bot.wuliang.otherUtil.OtherUtil.STConversion.toMd5
import bot.wuliang.tencentCos.CosFileServiceImpl
import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Service

/** 走势图发送 */
@Service
class MarketTrendImageService(
    private val trends: MarketTrendService,
    private val webImgUtil: WebImgUtil,
    private val cos: CosFileServiceImpl,
    private val mapper: ObjectMapper,
) {
    private val rendering = Semaphore(1)

    suspend fun imageUrl(request: MarketTrendRequest): String = rendering.withPermit {
        withContext(Dispatchers.IO) {
            val report = trends.query(request)
            if (report.points.isEmpty()) {
                val period = if (report.window == "48hours") "48小时" else "90天"
                throw MarketTrendUnavailableException("「${report.name}」${report.variantLabel}近${period}暂无成交记录，可切换时间范围或等级。")
            }
            val imageName = "market-trend-${mapper.writeValueAsString(report).toMd5()}"
            cos.ensureLifecycleRule("market-trend-", 1)
            val url = webImgUtil.getImgUrl(
                WebImgUtil.ImgData(
                    url = "http://${webImgUtil.frontendAddress}/marketTrend",
                    data = mapper.writeValueAsString(report), imgName = imageName,
                    element = "#app", waitElement = ".market-trend-image[data-ready='true']",
                )
            )
            check(url.isNotBlank() && cos.checkFileExist(imageName, "jpeg")) { "趋势图片上传失败" }
            url
        }
    }
}