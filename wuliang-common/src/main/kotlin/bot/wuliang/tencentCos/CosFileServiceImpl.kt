package bot.wuliang.tencentCos

import bot.wuliang.botLog.logUtil.LoggerUtils.logError
import com.qcloud.cos.exception.CosClientException
import com.qcloud.cos.exception.CosServiceException
import com.qcloud.cos.model.*
import com.qcloud.cos.model.lifecycle.LifecycleFilter
import com.qcloud.cos.model.lifecycle.LifecyclePrefixPredicate
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.util.*
import java.util.concurrent.ConcurrentHashMap


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
    private val lifecycleRuleConfigured = ConcurrentHashMap<String, Boolean>()

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
        return try {
            FileInputStream(file).use { inputStream ->
                uploadFile(file.name, inputStream, mime, inputStream.channel.size())
            }
        } catch (e: IOException) {
            logError("文件上传失败：", e)
            ""
        }
    }


    override fun uploadFile(
        fileName: String?,
        inputStream: InputStream?,
        mime: String,
        contentLength: Long
    ): String {
        var thisFileName = fileName
        var url = ""
        checkNotNull(thisFileName)
        requireNotNull(inputStream) { "上传输入流不能为空" }
        require(contentLength >= 0) { "上传内容长度不能为负数" }
        thisFileName = txCosConfig.path + fileName + ".$mime"
        // 判断上传的文件是否是合法的
        if (IMG_SUFFIX.contains(mime) || VIDEO_SUFFIX.contains(mime) || AUDIO_SUFFIX.contains(mime)) {
            val transferManager = txCosConfig.createTransferManager(cosClient)

            val bucketName: String = txCosConfig.bucketName //储存桶名称
            val metadata = ObjectMetadata().apply {
                this.contentLength = contentLength
            }
            val putObjectRequest = PutObjectRequest(bucketName, thisFileName, inputStream, metadata)

            try {
                val upload = transferManager.upload(putObjectRequest) //上传
                upload.waitForUploadResult()
                url = txCosConfig.url + thisFileName
            } catch (e: CosServiceException) {
                logError("COS文件上传失败: key=$thisFileName", e)
            } catch (e: CosClientException) {
                logError("COS文件上传失败: key=$thisFileName", e)
            } catch (e: InterruptedException) {
                Thread.currentThread().interrupt()
                logError("COS文件上传被中断: key=$thisFileName", e)
            } finally {
                transferManager.shutdownNow(false)
            }
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
                val metadata = ObjectMetadata().apply {
                    contentLength = uploadFile.size
                }
                uploadFile.inputStream.use { inputStream ->
                    cosClient.putObject(bucketName, key, inputStream, metadata)
                }
                //设置输出信息
                return txCosConfig.url + filePath
            } catch (e: Exception) {
                logError("COS文件上传失败: key=$filePath", e)
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

    /**
     * 为指定文件名前缀的对象配置生命周期规则，到期的对象由 COS 自动删除
     */
    fun ensureLifecycleRule(fileNamePrefix: String, expireDays: Int) {
        if (lifecycleRuleConfigured[fileNamePrefix] == true) return
        val prefix = txCosConfig.path + fileNamePrefix
        val ruleId = "expire-${fileNamePrefix.trimEnd('-')}-${expireDays}d"
        try {
            val existingRules = try {
                cosClient.getBucketLifecycleConfiguration(GetBucketLifecycleConfigurationRequest(txCosConfig.bucketName))
                    ?.rules?.toMutableList() ?: mutableListOf()
            } catch (e: CosServiceException) {
                if (e.statusCode == 404) mutableListOf() else throw e
            }
            if (existingRules.none { it.id == ruleId }) {
                val rule = BucketLifecycleConfiguration.Rule().apply {
                    id = ruleId
                    status = BucketLifecycleConfiguration.ENABLED
                    expirationInDays = expireDays
                    filter = LifecycleFilter(LifecyclePrefixPredicate(prefix))
                }
                existingRules.add(rule)
                cosClient.setBucketLifecycleConfiguration(
                    SetBucketLifecycleConfigurationRequest(
                        txCosConfig.bucketName,
                        BucketLifecycleConfiguration(existingRules)
                    )
                )
            }
            lifecycleRuleConfigured[fileNamePrefix] = true
        } catch (e: Exception) {
            logError("配置COS生命周期规则失败: prefix=$prefix, expireDays=$expireDays", e)
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