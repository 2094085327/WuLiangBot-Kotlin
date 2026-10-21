package bot.wuliang.market.presentation

import bot.wuliang.imageProcess.WebImgUtil
import bot.wuliang.market.history.MarketRankingRequest
import bot.wuliang.market.history.MarketRankingService
import bot.wuliang.otherUtil.OtherUtil.STConversion.toMd5
import bot.wuliang.tencentCos.CosFileServiceImpl
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Service

class MarketRankingUnavailableException(message: String) : RuntimeException(message)

/** 读取一次已发布快照并注入图片页；同日补采、分页和筛选改变时均刷新图片缓存。 */
@Service
class MarketRankingImageService(
    private val ranking: MarketRankingService,
    private val webImgUtil: WebImgUtil,
    private val cos: CosFileServiceImpl,
    objectMapper: ObjectMapper,
) {
    private val mapper = objectMapper.copy().registerModule(JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)

    // 限制本进程的截图并发，重复查询排队后复用 COS 中已生成的图片。
    private val rendering = Semaphore(1)

    suspend fun imageUrl(request: MarketRankingRequest): String = rendering.withPermit {
        withContext(Dispatchers.IO) {
            val report = ranking.query(request)
            if (report.tradeDay == null || report.publication == null) {
                throw MarketRankingUnavailableException("排行榜数据正在同步，首个完整日发布后即可查询，请稍后再试。")
            }
            if (report.sections.all { it.items.isEmpty() }) {
                throw MarketRankingUnavailableException(
                    if (request.page > 1) "第${request.page}页暂无成交记录，请查询前一页。"
                    else "${report.tradeDay} 的所选分类和规格暂无成交记录，可以切换等级或排序指标。"
                )
            }
            // 生成时间和 Redis 标记恢复时间不影响画面；成交内容与缺项数仍参与缓存键。
            val cacheReport = report.copy(
                generatedAt = 0,
                publication = report.publication.copy(publishedAt = 0, recovered = false),
            )
            val fingerprint = mapper.writeValueAsString(cacheReport).toMd5()
            val imageName = "market-ranking-v1-${report.tradeDay}-$fingerprint"
            cos.ensureLifecycleRule("market-ranking-", 1)
            val url = webImgUtil.getImgUrl(
                WebImgUtil.ImgData(
                    url = "http://${webImgUtil.frontendAddress}/marketRanking",
                    data = mapper.writeValueAsString(report),
                    imgName = imageName,
                    element = ".market-ranking-image",
                    waitElement = ".market-ranking-image[data-ready='true']",
                )
            )
            // 既有上传工具可能吞掉 COS 上传异常，确认对象存在后再发图片链接。
            check(url.isNotBlank() && cos.checkFileExist(imageName, "jpeg")) { "排行榜图片上传失败" }
            url
        }
    }
}