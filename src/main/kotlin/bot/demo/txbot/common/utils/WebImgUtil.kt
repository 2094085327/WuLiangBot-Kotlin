package bot.demo.txbot.common.utils

import bot.demo.txbot.common.qiNiuCos.QiNiuService
import bot.demo.txbot.other.IMG_CACHE_PATH
import com.idrsolutions.image.png.PngCompressor
import com.luciad.imageio.webp.WebPWriteParam
import com.microsoft.playwright.Browser
import com.microsoft.playwright.ElementHandle
import com.microsoft.playwright.Page
import com.microsoft.playwright.Playwright
import com.mikuac.shiro.common.utils.MsgUtils
import com.mikuac.shiro.core.Bot
import com.mikuac.shiro.dto.event.message.AnyMessageEvent
import net.coobird.thumbnailator.Thumbnails
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import pers.wuliang.robot.common.utils.LoggerUtils.logError
import pers.wuliang.robot.common.utils.LoggerUtils.logInfo
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*
import java.util.logging.Logger
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import javax.imageio.stream.FileImageOutputStream


/**
 *@Description: 进行浏览器绘图的主要工具类
 *@Author zeng
 *@Date 2023/6/11 15:05
 *@User 86188
 */
@Component
//@Configuration
class WebImgUtil(
    @Autowired private val qiNiuService: QiNiuService,
    @Value("\${web_config.port}") var usePort: String,
    @Value("\${web_config.img_bed_path}") var imgBedPath: String
) {
//    companion object {
//        var usePort: String = ""
//        var imgBedPath: String = ""
//    }

    /**
     * 图片相关数据
     *
     * @param url 网址链接
     * @param element 指定截图元素
     * @param imgName 图片名称
     * @param imgPath 图片存储路径
     * @param width 图片宽度
     * @param height 图片高度
     * @param scale 缩放等级
     * @param sleepTime 等待时间
     */
    data class ImgData(
        val url: String,
        val element: String? = "body",
        val imgName: String? = null,
        val imgPath: String? = null,
        val width: Int? = null,
        val height: Int? = null,
        val scale: Double? = null,
        var imageType: String? = "jpeg",
        var sleepTime: Long = 0
    )

    private val logger: Logger = Logger.getLogger(WebImgUtil::class.java.getName())

//    @Value("\${web_config.port}")
//    fun setPort(port: String) {
//        usePort = port
//    }

//    @Value("\${web_config.img_bed_path}")
//    fun setImgBedPath(path: String) {
//        imgBedPath = path
//    }


    fun convertImageToBase64(imagePath: String): String {
        val bytes = Files.readAllBytes(Paths.get(imagePath))
        return Base64.getEncoder().encodeToString(bytes)
    }

    fun outputStreamToBase64(outputStream: ByteArray): String {
        return "base64://${Base64.getEncoder().encodeToString(outputStream)}"
    }


    /**
     * 发送缓存图片
     *
     * @param bot 机器人
     * @param event 事件
     * @param imgNameTmp 图片缓存文件名
     */
    fun sendCachedImage(bot: Bot, event: AnyMessageEvent?, imgNameTmp: String) {
        val sendCacheImg: String = MsgUtils
            .builder()
            .img(readTmpImgFile(imgNameTmp))
            .build()

        bot.sendMsg(event, sendCacheImg, false)
        logger.info("使用缓存文件:$imgNameTmp")
    }


    /**
     *删除超过缓存时间的图片
     **/
    fun deleteImgCache() {
        val folderPath = "resources/imageCache"
        val folder = File(folderPath)
        val currentDate = Date()
        val fiveMinutesAgo = Date(currentDate.time - 5 * 60 * 1000)
        folder.listFiles()?.forEach { file ->
            if (file.lastModified() < fiveMinutesAgo.time) {
                logger.info("删除缓存：${file.name}")
                file.delete()
            }
        }
    }

    /**
     * 将 BufferedImage 转换为字节数组
     *
     * @param image 图片流
     * @param formatName 图片格式
     * @return ByteArray字节数组
     */
    fun bufferedImageToByteArray(image: BufferedImage, formatName: String): ByteArray {
        val byteArrayOutputStream = ByteArrayOutputStream()

        try {
            // 将 BufferedImage 写入 ByteArrayOutputStream
            ImageIO.write(image, formatName, byteArrayOutputStream)

            // 获取 ByteArrayOutputStream 中的字节数组
            val byteArray = byteArrayOutputStream.toByteArray()

            // 关闭 ByteArrayOutputStream
            byteArrayOutputStream.close()

            return byteArray
        } catch (e: Exception) {
            // 处理异常
            e.printStackTrace()
            return byteArrayOf() // 返回空数组或其他默认值
        }
    }


    /**
     * 将字节数组转换为JPEG图片并保存到本地
     * @param byteArray 图片字节数组
     * @param imgName 图片名
     * @param imgType 图片类型
     * @param imgPath 图片存储路径
     *
     * @return 本地文件路径
     */
    fun turnByteToJpeg(byteArray: ByteArray, imgName: String, imgType: String, imgPath: String? = null): String {
        deleteImgCache()

        val folderPath = IMG_CACHE_PATH
        val screenshotFilePath =
            if (imgPath != null) File("$imgPath/$imgName.$imgType") else File("$folderPath/$imgName.$imgType")
        val folder = File(folderPath)
        if (!folder.exists()) folder.mkdirs()


        val byteArrayInputStream = ByteArrayInputStream(byteArray)
        val originalImage: BufferedImage = ImageIO.read(byteArrayInputStream)

        // 创建一个新的BufferedImage对象，指定为JPEG格式
        val jpegImage = BufferedImage(originalImage.width, originalImage.height, BufferedImage.TYPE_INT_RGB)

        // 将原始图像绘制到新的JPEG图像上
        jpegImage.graphics.drawImage(originalImage, 0, 0, null)
        ImageIO.write(jpegImage, imgType, screenshotFilePath)
        return screenshotFilePath.absolutePath
    }

    /**
     * 返回Base64图片
     *
     * @param imgData 图片数据
     * @return Base64图片地址
     */
    @Suppress("unused")
    fun returnBs4Img(imgData: ImgData): String? {
        val imagePath = getImgFromWeb(imgData)
        return "base64://${convertImageToBase64("${imagePath.split(".")[0]}.${imgData.imageType}")}"
    }

    /**
     * 图床发送图片，如果不使用Telegraph-Image（自行搜索），请自行实现图床逻辑
     *
     * @param imgData 图片数据
     * @return 图片Url
     */
    fun returnUrlImg(imgData: ImgData): String? {
        val imagePath = getImgFromWeb(imgData)
        val imgJson = HttpUtil.doPostJson(
            url = "${imgBedPath}/upload",
            files = mapOf("file" to File(imagePath)),
            headers = mutableMapOf("content-type" to "multipart/form-data;")
        )
        val imgUrl = "${imgBedPath}${imgJson[0]["src"].textValue()}"

        creatTmpImgFile(imgData.imgName ?: System.currentTimeMillis().toString(), imgUrl)
        return imgUrl
    }

    fun creatTmpImgFile(imgName: String, input: String) {
        val tempDir = File(IMG_CACHE_PATH)
        val tempFile = File.createTempFile(imgName, null, tempDir)
        try {
            tempFile.writeText(input)
            logInfo("写入缓存文件:${tempFile.name}")
        } catch (e: IOException) {
            logError("写入缓存文件失败:$e")
        }
    }

    fun readTmpImgFile(imgName: String): String? {
        val directory = File(IMG_CACHE_PATH)

        // 查找以imgName开头的所有文件
        val matchingFiles = directory.listFiles { file ->
            file.name.startsWith(imgName) && file.extension == "tmp"
        } ?: return null

        val fileToRead = matchingFiles[0]
        return try {
            fileToRead.readText(Charsets.UTF_8)
        } catch (e: IOException) {
            logError("读取文件失败:${fileToRead.path}: $e")
            null
        }
    }

    fun getImgByte(imgData: ImgData): ByteArray {
        Playwright.create().use { playwright ->
            val browser: Browser = playwright.chromium().launch()
            val page: Page = browser.newPage()
//            val realImgName = imgData.imgName ?: System.currentTimeMillis().toString()
            page.navigate(imgData.url)
            var byteArray = page.screenshot(
                Page.ScreenshotOptions()
                    .setFullPage(true)
            )
            if (imgData.element != null) {
                page.waitForSelector(imgData.element)
                val body: ElementHandle = page.querySelector(imgData.element)!!

                // 截图
                byteArray = body.screenshot(ElementHandle.ScreenshotOptions())
            }


            if (imgData.scale != null) {
                val thumbnailBuilder = Thumbnails.of(byteArray.inputStream()).scale(imgData.scale).asBufferedImage()
                byteArray = bufferedImageToByteArray(thumbnailBuilder, imgData.imageType!!)
            }

            return byteArray
        }
    }

    /**
     * 从网页获取截图
     *
     * @param imgData 图片数据
     * @return Base64链接
     */
    fun getImgFromWeb(imgData: ImgData): String {
//        Playwright.create().use { playwright ->
//            val browser: Browser = playwright.chromium().launch()
//            val page: Page = browser.newPage()
        val realImgName = imgData.imgName ?: System.currentTimeMillis().toString()
//            page.navigate(imgData.url)
//            var byteArray = page.screenshot(
//                Page.ScreenshotOptions()
//                    .setFullPage(true)
//            )
//            if (imgData.element != null) {
//                page.waitForSelector(imgData.element)
//                val body: ElementHandle = page.querySelector(imgData.element)!!
//
//                // 截图
//                byteArray = body.screenshot(ElementHandle.ScreenshotOptions())
//            }
//
//
//            if (imgData.scale != null) {
//                val thumbnailBuilder = Thumbnails.of(byteArray.inputStream()).scale(imgData.scale).asBufferedImage()
//                byteArray = bufferedImageToByteArray(thumbnailBuilder, imgData.imageType!!)
//            }

        val realImgPath =
            turnByteToJpeg(
                byteArray = getImgByte(imgData),
                imgName = realImgName,
                imgType = imgData.imageType!!,
                imgPath = imgData.imgPath
            )

        return realImgPath
//        }
    }

//    fun downloadImageFromUrl(imageUrl: String): ByteArray {
//        val client = OkHttpClient()
//        val request = Request.Builder().url(imageUrl).build()
//
//        client.newCall(request).execute().use { response ->
//            if (!response.isSuccessful) throw IOException("下载图片失败: $response")
//
//            return response.body.bytes()
//        }
//    }

    fun returnUrlImgByQiNiu(imgData: ImgData): String? {
        val byte = getImgByte(imgData)
        return qiNiuService.upload(byte, imgData.imgName)
    }


    @Suppress("unused")
    fun turnPngToWebp(imagePath: String, scale: Float = 0.8f) {
        try {
            val imageFile = File(imagePath)
            val image = ImageIO.read(imageFile)
            val writer = ImageIO.getImageWritersByMIMEType("image/webp").next()
            val writeParam = WebPWriteParam(writer.locale)
            writeParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT)
            // 设置有损压缩
            writeParam.compressionType = writeParam.getCompressionTypes()[WebPWriteParam.LOSSY_COMPRESSION]
            //设置 80% 的质量. 设置范围 0-1
            writeParam.compressionQuality = scale

            writer.setOutput(FileImageOutputStream(File("${imagePath.split(".")[0]}.webp")))
            writer.write(null, IIOImage(image, null, null), writeParam)
            imageFile.delete()
        } catch (e: IOException) {
            throw RuntimeException(e)
        }
    }

    @Suppress("unused")
    fun compressImage(imagePath: String) {
        val file = File(imagePath)
        val outfile = File(imagePath)
        PngCompressor.compress(file, outfile)
    }

    fun sendNewImage(bot: Bot, event: AnyMessageEvent?, imgData: ImgData) {
        val imgUrl = returnUrlImgByQiNiu(imgData)
        val sendMsg: String = MsgUtils.builder().img(imgUrl).build()
        bot.sendMsg(event, sendMsg, false)
    }

}