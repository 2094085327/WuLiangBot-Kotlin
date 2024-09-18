package bot.demo.txbot.genShin.util

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import javax.servlet.http.HttpServletResponse


@Controller
class GenshinImageController {
    @GetMapping("/images/permanents/{imageName}")
    @Throws(IOException::class)
    fun getPermanentsImage(@PathVariable imageName: String, response: HttpServletResponse) {
        serveImage("permanents", imageName, response)
    }

    @GetMapping("/images/role/{imageName}")
    @Throws(IOException::class)
    fun getRoleImage(@PathVariable imageName: String, response: HttpServletResponse) {
        serveImage("role", imageName, response)
    }

    @GetMapping("/images/sources/{imageName}")
    @Throws(IOException::class)
    fun getSourcesImage(@PathVariable imageName: String, response: HttpServletResponse) {
        serveImage("sources", imageName, response)
    }

    @GetMapping("/images/weapons/{imageName}")
    @Throws(IOException::class)
    fun getWeaponsImage(@PathVariable imageName: String, response: HttpServletResponse) {
        serveImage("weapons", imageName, response)
    }

    @Throws(IOException::class)
    private fun serveImage(folderName: String, imageName: String, response: HttpServletResponse) {
        // 读取图片并写入响应
        if (imageName == "null.png") return
        val file = File(EXTERNAL_FOLDER_PATH + File.separator + folderName + File.separator + imageName)
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

    companion object {
        private const val EXTERNAL_FOLDER_PATH = "resources/genShin/GenShinImg"
    }
}