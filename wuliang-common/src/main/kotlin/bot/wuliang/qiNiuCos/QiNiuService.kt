package bot.wuliang.qiNiuCos

import bot.wuliang.imageProcess.WebImgUtil
import com.qiniu.storage.model.FileInfo
import java.io.File
import java.io.InputStream


/**
 * @description: 七牛云文件上传服务接口
 * @author Nature Zero
 * @date 2024/7/19 上午10:00
 */
//@Service
interface QiNiuService {
    fun getUpToken(fileName: String): String

    fun upload(file: File, fileName: String? = null, mime: String? = "jpeg"): String

    fun upload(filePath: String, fileName: String? = null, mime: String? = "jpeg"): String

    fun upload(byteArray: ByteArray, fileName: String? = null, mime: String? = "jpeg"): String

    fun upload(inputStream: InputStream, fileName: String? = null, mime: String? = "jpeg"): String

    fun deleteFile(fileName: String, mime: String? = "jpeg")

    fun getFileInfo(imgData: WebImgUtil.ImgData): FileInfo

    fun returnFilePath(fileName: String?, mime: String? = "jpeg"): String

    fun returnFileUrl(filePath: String): String
}