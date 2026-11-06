package bot.wuliang.riven.analysis

import bot.wuliang.imageProcess.WebImgUtil
import bot.wuliang.tencentCos.CosFileServiceImpl
import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Service
import java.util.*

@Service
class RivenAnalysisImageService(
    private val images: WebImgUtil,
    private val cos: CosFileServiceImpl,
    private val mapper: ObjectMapper,
) {
    private val rendering = Semaphore(1)

    suspend fun imageUrl(report: RivenAnalysisReport): String = rendering.withPermit {
        withContext(Dispatchers.IO) {
            cos.ensureLifecycleRule("riven-analysis-", 1)
            val name = "riven-analysis-${UUID.randomUUID()}"
            val result = images.getImgUrl(
                WebImgUtil.ImgData(
                    url = "http://${images.frontendAddress}/rivenAnalysis",
                    data = mapper.writeValueAsString(report), imgName = name, width = 660,
                    element = "#app", waitElement = ".riven-analysis-image[data-ready='true']",
                )
            )
            check(result.isNotBlank() && cos.checkFileExist(name, "jpeg")) { "紫卡分析图片上传失败" }
            result
        }
    }
}