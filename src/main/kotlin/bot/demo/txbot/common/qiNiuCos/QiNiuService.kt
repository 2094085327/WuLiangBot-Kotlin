package bot.demo.txbot.common.qiNiuCos

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

    fun upload(file: File, fileName: String? = null): String

    fun upload(filePath: String, fileName: String? = null): String

    fun upload(byteArray: ByteArray, fileName: String? = null): String

    fun upload(inputStream: InputStream, fileName: String? = null,mime:String?=null): String

}