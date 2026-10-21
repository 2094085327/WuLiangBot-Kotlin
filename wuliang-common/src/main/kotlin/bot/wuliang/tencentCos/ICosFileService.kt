package bot.wuliang.tencentCos

import org.springframework.web.multipart.MultipartFile
import java.io.File
import java.io.IOException
import java.io.InputStream


interface ICosFileService {
    fun uploadFile(file: File, mime: String): String?

    /**
     * 上传已知长度的输入流，contentLength 为剩余待上传数据的字节数；调用方负责关闭输入流。
     */
    fun uploadFile(fileName: String?, inputStream: InputStream?, mime: String, contentLength: Long): String?

    @Throws(IOException::class)
    fun uploadFileWithFolder(folder: String, uploadFile: MultipartFile): String?

    fun deleteFile(fileName: String, mime: String)

    fun checkFileExist(fileName: String, mime: String): Boolean

    fun getFileUrl(fileName: String, mime: String): String?
}