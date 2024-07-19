package bot.demo.txbot.common.qiNiuCos

import com.qiniu.storage.Configuration
import com.qiniu.storage.Region
import com.qiniu.storage.UploadManager
import com.qiniu.util.Auth
import org.springframework.beans.factory.annotation.Value


/**
 * @description: 七牛云配置
 * @author Nature Zero
 * @date 2024/7/18 下午10:09
 */
@org.springframework.context.annotation.Configuration
class QiNiuCosConfig(
    @Value("\${qi_niu.cos.access_key}") val accessKey: String,
    @Value("\${qi_niu.cos.secret_key}") val secretKey: String,
    @Value("\${qi_niu.cos.bucket}") val bucketName: String,
    @Value("\${qi_niu.cos.url}") val url: String,
    @Value("\${qi_niu.cos.path}") val path: String,
    @Value("\${qi_niu.cos.region}") val region: String,
    @Value("\${qi_niu.cos.code_format}") private val codeFormat: String,
    @Value("\${qi_niu.cos.policy_expire}") val policyExpire: Long,
) {
    val auth: Auth = Auth.create(this.accessKey, this.secretKey)

    private var cfg: Configuration = Configuration(Region.autoRegion())
    var uploadManager = UploadManager(cfg)
}