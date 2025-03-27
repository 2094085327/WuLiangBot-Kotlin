package bot.wuliang.tencentCos

import com.qcloud.cos.COSClient
import com.qcloud.cos.ClientConfig
import com.qcloud.cos.auth.BasicCOSCredentials
import com.qcloud.cos.auth.COSCredentials
import com.qcloud.cos.region.Region
import com.qcloud.cos.transfer.TransferManager
import com.qcloud.cos.transfer.TransferManagerConfiguration
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.Executors


/**
 * @description: 腾讯存储桶配置
 * @author Nature Zero
 * @date 2      024/7/18 下午1:53
 */
@Configuration
class TxCosConfig(
    @Value("\${tencent.cos.secret_id}") val secretId: String,
    @Value("\${tencent.cos.secret_key}") val secretKey: String,
    @Value("\${tencent.cos.region}") val region: String,
    @Value("\${tencent.cos.bucket}") val bucketName: String,
    @Value("\${tencent.cos.url}") val url: String,
    @Value("\${tencent.cos.path}") val path: String,
    @Value("\${tencent.cos.policy_expire}") val policyExpire: Int,
    @Value("\${tencent.cos.code_format}")
    private val codeFormat: String,
) {
    @Bean // 创建 COSClient 实例
    fun cosClient(): COSClient {
        // 初始化用户身份信息（secretId, secretKey）。
        val cred: COSCredentials = BasicCOSCredentials(this.secretId, this.secretKey)
        // 设置 bucket 的区域
        val region = Region(this.region)
        val clientConfig = ClientConfig(region)
        // 生成 cos 客户端
        val cosClient = COSClient(cred, clientConfig)
        return cosClient
    }

    // 创建 TransferManager 实例，这个实例用来后续调用高级接口
    fun createTransferManager(cosClient: COSClient): TransferManager {
        // 自定义线程池大小，建议在客户端与 COS 网络充足（例如使用腾讯云的 CVM，同地域上传 COS）的情况下，设置成16或32即可，可较充分的利用网络资源
        // 对于使用公网传输且网络带宽质量不高的情况，建议减小该值，避免因网速过慢，造成请求超时。
        val threadPool = Executors.newFixedThreadPool(32)

        // 传入一个 线程池, 若不传入线程池，默认 TransferManager 中会生成一个单线程的线程池。
        val transferManager = TransferManager(cosClient, threadPool)

        // 设置高级接口的配置项
        // 分块上传阈值和分块大小分别为 5MB 和 1MB
        val transferManagerConfiguration = TransferManagerConfiguration()
        transferManagerConfiguration.multipartUploadThreshold = (5 * 1024 * 1024).toLong()
        transferManagerConfiguration.minimumUploadPartSize = (1 * 1024 * 1024).toLong()
        transferManager.configuration = transferManagerConfiguration
        return transferManager
    }

    fun shutdownTransferManager(transferManager: TransferManager) {
        transferManager.shutdownNow(false)
    }

}