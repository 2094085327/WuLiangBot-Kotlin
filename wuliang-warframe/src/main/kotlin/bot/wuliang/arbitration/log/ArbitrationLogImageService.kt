package bot.wuliang.arbitration.log

import bot.wuliang.imageProcess.WebImgUtil
import bot.wuliang.arbitration.log.history.ArbitrationLogHistoryReport
import bot.wuliang.tencentCos.CosFileServiceImpl
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import java.util.*

@Service
class ArbitrationLogImageService(
    private val images: WebImgUtil,
    private val cos: CosFileServiceImpl,
    private val mapper: ObjectMapper,
) {
    fun imageUrl(report: ArbitrationLogReport): String {
        return render(report, "arbitration-log-", "arbitrationLog", ".arbitration-log-image[data-ready='true']")
    }

    fun historyImageUrl(report: ArbitrationLogHistoryReport): String {
        return render(report, "arbitration-history-", "arbitrationHistory", ".arbitration-history-image[data-ready='true']")
    }

    private fun render(report: Any, prefix: String, route: String, waitElement: String): String {
        cos.ensureLifecycleRule("arbitration-", 1)
        val name = "$prefix${UUID.randomUUID()}"
        val url = images.getImgUrl(
            WebImgUtil.ImgData(
                url = "http://${images.frontendAddress}/$route",
                data = mapper.writeValueAsString(report), imgName = name, width = 760,
                element = "#app", waitElement = waitElement,
            )
        )
        check(url.isNotBlank() && cos.checkFileExist(name, "jpeg")) { "仲裁日志图片上传失败" }
        return url
    }
}