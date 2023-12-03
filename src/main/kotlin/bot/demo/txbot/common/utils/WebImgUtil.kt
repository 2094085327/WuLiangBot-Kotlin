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
//@PropertySource("classpath:config.properties")
//@PropertySource("classpath:application.yaml")
@Configuration
open class WebImgUtil {
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

//    fun getImg(url: String, width: Int? = null, height: Int? = null, sleepTime: Long = 0): ByteArray {
//
//        WebDriverManager.chromedriver().setup()
//        println(WebDriverManager.chromedriver().downloadedDriverPath)
//        println(WebDriverManager.chromedriver().downloadedDriverVersion)
//        val options = ChromeOptions()
//        options.addArguments("--remote-allow-origins=*")
//        options.addArguments("--headless")
//        options.addArguments("--disable-gpu")
//        options.addArguments("--no-sandbox")
//        options.addArguments("--disable-dev-shm-usage")
//        options.addArguments("--start-maximized")
//        // 创建ChromeDriver实例
//        val driver = ChromeDriver(options)
//
//        // 访问本地网页
//        driver.get(url)
//
//        val widths = driver.executeScript("return document.documentElement.scrollWidth") as Long
//        val heights = driver.executeScript("return document.documentElement.scrollHeight") as Long
//        println("高度：$heights 宽度：$widths")
//
//        val actualWidth = width ?: widths.toInt()
//        val actualHeight = height ?: heights.toInt()
//
//        // 设置窗口大小
//        driver.manage().window().size = org.openqa.selenium.Dimension(actualWidth, actualHeight)
//
//        // 等待页面加载完成
//        Thread.sleep(sleepTime)
//
//        val srcFile: ByteArray = driver.getScreenshotAs(OutputType.BYTES)
//        val bais = ByteArrayInputStream(srcFile)
//        val image = ImageIO.read(bais)
//        val writer = ImageIO.getImageWritersByFormatName("png").next()
//        val iwp = writer.defaultWriteParam
//        iwp.compressionMode = ImageWriteParam.MODE_EXPLICIT
//        iwp.compressionQuality = 1f //Adjust the quality here
//        val baos = ByteArrayOutputStream()
//        writer.output = ImageIO.createImageOutputStream(baos)
//        writer.write(null, IIOImage(image, null, null), iwp)
//
//        val bytes = baos.toByteArray()
//
//        Thread.sleep(500)
//        // 关闭浏览器
//        driver.quit()
//        return bytes
//    }


    private val headers: Map<String, String> = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0;Win64;x64)AppleWebKit/537.36 (KHTML,like Gecko)Chrome/119.0.0.0 Safari/537.36 Edg/119.0.0.0",
        "Authorization" to key,
        "Content-Type" to "multipart/form-data"
    )
    private val baseUrl = "https://sm.ms/api/v2"


    fun convertImageToBase64(imagePath: String): String {
        val bytes = Files.readAllBytes(Paths.get(imagePath))
        return Base64.getEncoder().encodeToString(bytes)
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


    fun getImgFromWeb(url: String, channel: Boolean, width: Int? = null, height: Int? = null, sleepTime: Long = 0): String? {
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