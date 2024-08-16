package bot.demo.txbot.common.tencentCos

import org.springframework.web.multipart.MultipartFile
import java.io.File
import java.io.IOException
import java.io.InputStream


interface ICosFileService {
    fun uploadFile(file: File, mime: String): String?

    fun uploadFile(fileName: String?, inputStream: InputStream?, mime: String): String?

    @Throws(IOException::class)
    fun uploadFileWithFolder(folder: String, uploadFile: MultipartFile): String?

    fun deleteFile(fileName: String, mime: String)

    fun checkFileExist(fileName: String, mime: String): Boolean
}