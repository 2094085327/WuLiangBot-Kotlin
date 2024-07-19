package bot.demo.txbot.common.tencentCos

import bot.demo.txbot.common.utils.WebImgUtil
import com.qcloud.cos.COSClient
import com.qcloud.cos.ClientConfig
import com.qcloud.cos.auth.BasicCOSCredentials
import com.qcloud.cos.auth.COSCredentials
import com.qcloud.cos.exception.CosClientException
import com.qcloud.cos.exception.CosServiceException
import com.qcloud.cos.model.ObjectMetadata
import com.qcloud.cos.model.PutObjectRequest
import com.qcloud.cos.region.Region
import com.qcloud.cos.transfer.TransferManager
import com.qcloud.cos.transfer.TransferManagerConfiguration
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import pers.wuliang.robot.common.utils.LoggerUtils.logError
import java.io.*
import java.util.*
import java.util.concurrent.Executors


/**
 * @description: 腾讯云存储实现方法
 * @author Nature Zero
 * @date 2024/7/18 下午2:38
 */
@Service
class CosFileServiceImpl(
    @Autowired private val cosClient: COSClient,
    @Autowired private val txCosConfig: TxCosConfig
) : ICosFileService {

    companion object {
        // 视频后缀 校验视频格式
        const val VIDEO_SUFFIX: String =
            "wmv,avi,dat,asf,mpeg,mpg,rm,rmvb,ram,flv,mp4,3gp,mov,divx,dv,vob,mkv,qt,cpk,fli,flc,f4v,m4v,mod,m2t,swf,webm,mts,m2ts"

        // 图片格式
        const val IMG_SUFFIX: String = "jpg,png,jpeg,gif,svg"

        // 音频格式
        const val AUDIO_SUFFIX: String = "cda,wav,mp1,mp2,mp3,wma,vqf"
    }

    override fun uploadFile(file: File): String {
        var url = ""
        try {
            url = uploadFile(file.name, FileInputStream(file))
        } catch (e: IOException) {
            // 图片上传失败
            logError("图片上传失败：", e)
        }
        return url
    }


    override fun uploadFile(fileName: String?, inputStream: InputStream?): String {
        var thisFileName = fileName
        var url = ""
        checkNotNull(thisFileName)
        // 文件后缀，用于判断上传的文件是否是合法的
        val suffix = thisFileName.substringAfterLast(".")
        thisFileName = txCosConfig.path + fileName
        if (IMG_SUFFIX.contains(suffix) || VIDEO_SUFFIX.contains(suffix) || AUDIO_SUFFIX.contains(
                suffix
            )
        ) {
            // 1 初始化用户身份信息（secretId, secretKey）。
            val cred: COSCredentials = BasicCOSCredentials(txCosConfig.secretId, txCosConfig.secretKey)
            // 2 设置 bucket 的区域, COS 地域的简称请参照
            val region = Region(txCosConfig.region)
            val clientConfig = ClientConfig(region)
            // 3 生成 cos 客户端。
            val cosClient = COSClient(cred, clientConfig)
            // 自定义线程池大小，建议在客户端与 COS 网络充足（例如使用腾讯云的 CVM，同地域上传 COS）的情况下，设置成16或32即可，可较充分的利用网络资源
            // 对于使用公网传输且网络带宽质量不高的情况，建议减小该值，避免因网速过慢，造成请求超时。
            val threadPool = Executors.newFixedThreadPool(32)

            // 传入一个线程池, 若不传入线程池，默认 TransferManager 中会生成一个单线程的线程池。
            val transferManager = TransferManager(cosClient, threadPool)

            // 设置高级接口的配置项
            // 分块上传阈值和分块大小分别为 5MB 和 1MB
            val transferManagerConfiguration = TransferManagerConfiguration()
            transferManagerConfiguration.multipartUploadThreshold = (5 * 1024 * 1024).toLong()
            transferManagerConfiguration.minimumUploadPartSize = (1 * 1024 * 1024).toLong()
            transferManager.configuration = transferManagerConfiguration


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
                transferManager.shutdownNow(true)
            }

            url = txCosConfig.url + thisFileName
        } else {
            //错误的类型，返回错误提示
            logError("文件格式错误")
        }

        return url
    }

    override fun uploadFileByUrl(url: String): String {
        var bytes: ByteArray? = null // 声明为可空类型
        try {
            bytes = WebImgUtil().downloadImageFromUrl(url)
        } catch (e: Exception) {
            logError("图片下载错误: ${e.message}")
        }

        // 确保bytes不为null才继续操作
        if (bytes == null) {
            throw IllegalStateException("图片数据未成功下载")
        }

        // 假设所有图片都被当作.jpeg处理，或者根据实际情况动态确定文件扩展名
        return uploadFile(UUID.randomUUID().toString() + ".jpeg", ByteArrayInputStream(bytes))
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
            // 1 初始化用户身份信息（secretId, secretKey）
            val cred: COSCredentials = BasicCOSCredentials(txCosConfig.secretId, txCosConfig.secretKey)
            // 2 设置 bucket 的区域, COS 地域的简称请参照
            val region = Region(txCosConfig.region)
            val clientConfig = ClientConfig(region)
            // 3 生成 cos 客户端。
            val cosClient = COSClient(cred, clientConfig)
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
            } finally {
                // 关闭客户端(关闭后台线程)
                cosClient.shutdown()
            }
        } else {
            logError("请选择正确的文件格式")
        }
        return ""
    }

}