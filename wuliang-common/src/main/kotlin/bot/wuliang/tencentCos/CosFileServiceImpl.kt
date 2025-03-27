package bot.wuliang.tencentCos

import bot.wuliang.botLog.logUtil.LoggerUtils.logError
import com.qcloud.cos.exception.CosClientException
import com.qcloud.cos.exception.CosServiceException
import com.qcloud.cos.model.ObjectMetadata
import com.qcloud.cos.model.PutObjectRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.io.*
import java.util.*


/**
 * @description: 腾讯云存储实现方法
 * @author Nature Zero
 * @date 2024/7/18 下午2:38
 */
@Service
class CosFileServiceImpl(
    @Autowired private val txCosConfig: TxCosConfig,
) : ICosFileService {
    val cosClient = txCosConfig.cosClient()
    private val scope = CoroutineScope(Dispatchers.Default)

    companion object {
        // 视频后缀 校验视频格式
        const val VIDEO_SUFFIX: String =
            "wmv,avi,dat,asf,mpeg,mpg,rm,rmvb,ram,flv,mp4,3gp,mov,divx,dv,vob,mkv,qt,cpk,fli,flc,f4v,m4v,mod,m2t,swf,webm,mts,m2ts"

        // 图片格式
        const val IMG_SUFFIX: String = "jpg,png,jpeg,gif,svg"

        // 音频格式
        const val AUDIO_SUFFIX: String = "cda,wav,mp1,mp2,mp3,wma,vqf"
    }

    override fun uploadFile(file: File, mime: String): String {
        var url = ""
        try {
            url = uploadFile(file.name, FileInputStream(file), mime)
        } catch (e: IOException) {
            // 图片上传失败
            logError("图片上传失败：", e)
        }
        return url
    }


    override fun uploadFile(fileName: String?, inputStream: InputStream?, mime: String): String {
        var thisFileName = fileName
        var url = ""
        checkNotNull(thisFileName)
        thisFileName = txCosConfig.path + fileName + ".$mime"
        // 判断上传的文件是否是合法的
        if (IMG_SUFFIX.contains(mime) || VIDEO_SUFFIX.contains(mime) || AUDIO_SUFFIX.contains(mime)) {
            val transferManager = txCosConfig.createTransferManager(cosClient)

            val bucketName: String = txCosConfig.bucketName //储存桶名称
            val putObjectRequest = PutObjectRequest(bucketName, thisFileName, inputStream, ObjectMetadata())

            try {
                val upload = transferManager.upload(putObjectRequest) //上传
                val uploadResult = upload.waitForUploadResult()
                uploadResult.key //上传后的文件名字
            } catch (e: CosServiceException) {
                logError("上传连接获取失败:${e.printStackTrace()}")
            } catch (e: CosClientException) {
                logError("上传连接获取失败:${e.printStackTrace()}")
            } catch (e: InterruptedException) {
                logError("上传连接获取失败:${e.printStackTrace()}")
            } finally {
                transferManager.shutdownNow(false)
            }
            url = txCosConfig.url + thisFileName
        } else {
            //错误的类型，返回错误提示
            logError("文件格式错误")
        }

        return url
    }

    @Throws(IOException::class)
    override fun uploadFileWithFolder(folder: String, uploadFile: MultipartFile): String {
        if (Objects.isNull(uploadFile)) {
            throw IOException("文件为空")
        }
        val fileExtension = uploadFile.originalFilename?.substringAfterLast(".", "") ?: ""

        // 校验上传的格式
        // 封装Result对象，而且将文件的byte数组放置到result对象中
        if (IMG_SUFFIX.contains(fileExtension.lowercase(Locale.getDefault())) ||
            VIDEO_SUFFIX.contains(fileExtension.lowercase(Locale.getDefault())) ||
            AUDIO_SUFFIX.contains(fileExtension.lowercase(Locale.getDefault()))
        ) {
            // 文件新路径
            val fileName = uploadFile.originalFilename
            val filePath: String = txCosConfig.path + folder + "/" + fileName
            //上传文件
            try {
                // 指定要上传到的存储桶
                val bucketName: String = txCosConfig.bucketName
                // 指定要上传到 COS 上对象键
                val key = filePath
                //这里的key是查找文件的依据，妥善保管
                cosClient.putObject(bucketName, key, ByteArrayInputStream(uploadFile.bytes), null)
                //设置输出信息
                return txCosConfig.url + filePath
            } catch (e: Exception) {
                logError("上传连接获取失败:${e.printStackTrace()}")
            }
        } else {
            logError("请选择正确的文件格式")
        }
        return ""
    }

    override fun deleteFile(fileName: String, mime: String) {
        scope.launch {
            try {
                val key = txCosConfig.path + fileName + ".$mime"
                if (!cosClient.doesObjectExist(txCosConfig.bucketName, key)) return@launch
                cosClient.deleteObject(txCosConfig.bucketName, key)
            } catch (e: CosServiceException) {
                e.printStackTrace()
            } catch (e: CosClientException) {
                e.printStackTrace()
            }
        }
    }

    override fun checkFileExist(fileName: String, mime: String): Boolean {
        val key = txCosConfig.path + fileName + ".$mime"
        return cosClient.doesObjectExist(txCosConfig.bucketName, key)
    }

    override fun getFileUrl(fileName: String, mime: String): String {
        return txCosConfig.url + txCosConfig.path + fileName + ".$mime"
    }

}