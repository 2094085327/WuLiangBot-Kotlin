package bot.wuliang.riven.range

import bot.wuliang.imageProcess.WebImgUtil
import bot.wuliang.riven.analysis.RivenAnalysisException
import bot.wuliang.riven.analysis.RivenCombination
import bot.wuliang.tencentCos.CosFileServiceImpl
import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Service

@Service
class RivenRangeImageService(
    private val ranges: RivenRangeService,
    private val images: WebImgUtil,
    private val cos: CosFileServiceImpl,
    private val mapper: ObjectMapper,
) {
    private val rendering = Semaphore(1)

    suspend fun imageUrl(type: String, combination: RivenCombination): String = withContext(Dispatchers.IO) {
        val name = "riven-range-$type-${combination.key}"
        if (cos.checkFileExist(name, "jpeg")) return@withContext cos.getFileUrl(name, "jpeg")
        if (!rendering.tryAcquire()) throw RivenAnalysisException("词条范围图片正在生成，请稍后重试。")
        try {
            // 取得生成名额后再检查，避免相邻请求重复截图。
            if (cos.checkFileExist(name, "jpeg")) return@withContext cos.getFileUrl(name, "jpeg")
            val result = images.getImgUrl(
                WebImgUtil.ImgData(
                    url = "http://${images.frontendAddress}/rivenRange",
                    data = mapper.writeValueAsString(ranges.report(type, combination)),
                    imgName = name, width = 1000, element = "#app",
                    waitElement = ".riven-range-image[data-ready='true']",
                )
            )
            check(result.isNotBlank() && cos.checkFileExist(name, "jpeg")) { "词条范围图片上传失败" }
            result
        } finally {
            rendering.release()
        }
    }
}