package bot.demo.txbot.common.tencentCos

import com.qcloud.cos.auth.COSCredentials
import org.springframework.web.multipart.MultipartFile
import java.io.File
import java.io.IOException
import java.io.InputStream


interface ICosFileService {
    fun uploadFile(file: File): String?

    fun uploadFile(fileName: String?, inputStream: InputStream?): String?

    /**
     * 根据url上传
     * @param url
     * @return String
     */
    fun uploadFileByUrl(url: String): String?


//    fun policy(): OssRecoverVO?

    @Throws(IOException::class)
    fun uploadFileWithFolder(folder: String, uploadFile: MultipartFile): String?
}