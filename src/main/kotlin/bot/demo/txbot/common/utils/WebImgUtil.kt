package bot.demo.txbot.common.utils

import com.fasterxml.jackson.databind.JsonNode
import com.microsoft.playwright.Browser
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*


/**
 *@Description: 进行浏览器绘图的主要工具类
 *@Author zeng
 *@Date 2023/6/11 15:05
 *@User 86188
 */
@Component
@Configuration
class WebImgUtil {
    companion object {
        var usePort: String = ""
        var key: String = ""
    }

    @Value("\${image_load.key}")
    fun getKey(imgKey: String) {
        key = imgKey
    }

    @Value("\${web_config.port}")
    fun setPort(port: String) {
        usePort = port
    }

    private val headers: MutableMap<String, Any> = mutableMapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0;Win64;x64)AppleWebKit/537.36 (KHTML,like Gecko)Chrome/119.0.0.0 Safari/537.36 Edg/119.0.0.0",
        "Authorization" to key,
        "Content-Type" to "multipart/form-data"
    )
    private val baseUrl = "https://sm.ms/api/v2"


    fun convertImageToBase64(imagePath: String): String {
        val bytes = Files.readAllBytes(Paths.get(imagePath))
        return Base64.getEncoder().encodeToString(bytes)
    }

    fun outputStreamToBase64(outputStream: ByteArray): String {
        return "base64://${Base64.getEncoder().encodeToString(outputStream)}"
    }


    fun loadImg(imgPath: String): JsonNode? {
        val files: Map<String, File> = mapOf("smfile" to File(imgPath))

        val params: Map<String, String> = mapOf("format" to "json")

        val imgData = HttpUtil.doPostJson(
            url = "$baseUrl/upload",
            files = files,
            params = params,
            headers = headers
        )

        return imgData["data"]
    }

    @Suppress("unused")
    fun removeImg(imgUrl: String) {
        HttpUtil.doGetStr(url = imgUrl, headers = headers)
        println("已经删除图片：$imgUrl")
    }


    fun getImgFromWeb(
        url: String,
        channel: Boolean,
        width: Int? = null,
        height: Int? = null,
        sleepTime: Long = 0
    ): String? {
        Playwright.create().use { playwright ->
            val browser: Browser = playwright.chromium().launch()
            val page: Page = browser.newPage()
            page.navigate(url)

            // 截图保存的文件名，可以根据需要修改
            val screenshotFileName = "screenshot.png"

            // 截图保存的文件路径
            val screenshotFilePath = File(screenshotFileName)

            val buffer = page.screenshot(
                Page.ScreenshotOptions()
                    .setFullPage(true)
            )

            // 将截图保存到本地文件
            FileOutputStream(screenshotFilePath).use { fos ->
                fos.write(buffer)
            }
            val imgPath = screenshotFilePath.absolutePath

            if (channel) return "base64://${convertImageToBase64(imgPath)}"
            else {
                val imgData = loadImg(imgPath)

                return imgData?.get("url")?.textValue()
            }
        }
    }
}