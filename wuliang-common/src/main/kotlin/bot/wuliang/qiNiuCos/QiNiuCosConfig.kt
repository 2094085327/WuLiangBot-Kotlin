package bot.wuliang.qiNiuCos

import com.qiniu.storage.BucketManager
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
    @Value("\${qi_niu.cos.access_key}") private val accessKey: String,
    @Value("\${qi_niu.cos.secret_key}") private val secretKey: String,
    @Value("\${qi_niu.cos.bucket}") val bucketName: String,
    @Value("\${qi_niu.cos.url}") val url: String,
    @Value("\${qi_niu.cos.path}") val path: String,
    @Value("\${qi_niu.cos.policy_expire}") val policyExpire: Long,
) {
    val auth: Auth by lazy {
        Auth.create(accessKey, secretKey)
    }

    private val cfg: Configuration by lazy {
        Configuration(Region.autoRegion())
    }

    val uploadManager: UploadManager by lazy {
        UploadManager(cfg)
    }

    val bucketManager: BucketManager by lazy {
        BucketManager(auth, cfg)
    }
}
