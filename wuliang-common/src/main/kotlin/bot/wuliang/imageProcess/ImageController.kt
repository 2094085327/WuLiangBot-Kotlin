package bot.wuliang.imageProcess

import bot.wuliang.config.RESOURCES_PATH
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import javax.servlet.http.HttpServletResponse


/**
 * @description: 向前端传递图片数据
 * @author Nature Zero
 * @date 2024/9/5 10:42
 */
@Controller
class ImageController {
    @GetMapping("/allImages/{imagePath}")
    @Throws(IOException::class)
    fun getAllImage(@PathVariable imagePath: String, response: HttpServletResponse) {
        serveImage(imagePath, response)
    }

    @GetMapping("/allImages/others/{imagePath}")
    @Throws(IOException::class)
    fun getOtherImage(@PathVariable imagePath: String, response: HttpServletResponse) {
        val newImagePath = "others" + File.separator + imagePath
        serveImage(newImagePath, response)
    }

    @Throws(IOException::class)
    private fun serveImage(imagePath: String?, response: HttpServletResponse) {
        // 读取图片并写入响应
        if (imagePath.isNullOrEmpty()) return
        val file = File(RESOURCES_PATH + File.separator + imagePath)
        FileInputStream(file).use { fis ->
            response.outputStream.use { os ->
                response.contentType = "image/png" // 适当修改图片类型
                val buffer = ByteArray(1024)
                var bytesRead: Int
                while (fis.read(buffer).also { bytesRead = it } != -1) {
                    os.write(buffer, 0, bytesRead)
                }
            }
        }
    }
}