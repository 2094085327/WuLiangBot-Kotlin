package bot.wuliang.arbitration

import bot.wuliang.imageProcess.WebImgUtil
import bot.wuliang.tencentCos.CosFileServiceImpl
import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class ArbitrationImageService(
    private val service: ArbitrationService,
    private val images: WebImgUtil,
    private val cos: CosFileServiceImpl,
    private val mapper: ObjectMapper,
) {
    private val rendering = Semaphore(1)

    suspend fun imageUrl(query: ArbitrationQuery): String = rendering.withPermit {
        withContext(Dispatchers.IO) {
            cos.ensureLifecycleRule("arbitration-", 1)
            if (query.table) tableImageUrl(query) else render(
                service.query(false, query.filter, query.page),
                "arbitration-${UUID.randomUUID()}",
            )
        }
    }

    private fun tableImageUrl(query: ArbitrationQuery): String {
        // 使用解析后的参数，省略页码、参数顺序及空白差异不会生成重复图片。
        val parameters = UUID.nameUUIDFromBytes("${query.filter}:${query.page}".toByteArray(Charsets.UTF_8))
        while (true) {
            // 排队结束后取时间，避免等待渲染的请求命中上一小时。
            val now = System.currentTimeMillis()
            val hour = now / HOUR_MILLIS
            val imageName = "arbitration-table-$hour-$parameters"
            val url = if (cos.checkFileExist(imageName, "jpeg")) {
                cos.getFileUrl(imageName, "jpeg")
            } else {
                val report = service.query(true, query.filter, query.page, now)
                if (report.items.isEmpty()) {
                    throw ArbitrationUnavailableException("已知排期内没有符合「${query.filter}」的仲裁，可以查询「仲裁表」。")
                }
                render(report, imageName)
            }
            // 查询云端或生成图片期间若跨过整点，重新取得新小时的图片。
            if (System.currentTimeMillis() / HOUR_MILLIS == hour) return url
        }
    }

    private fun render(report: ArbitrationReport, imageName: String): String {
        val url = images.getImgUrl(
            WebImgUtil.ImgData(
                url = "http://${images.frontendAddress}/${if (report.mode == "table") "arbitrationTable" else "arbitration"}",
                data = mapper.writeValueAsString(report), imgName = imageName, width = 660,
                element = "#app", waitElement = ".arbitration-image[data-ready='true']",
            )
        )
        check(url.isNotBlank() && cos.checkFileExist(imageName, "jpeg")) { "仲裁图片上传失败" }
        return url
    }

    private companion object {
        const val HOUR_MILLIS = 60 * 60 * 1000L
    }
}