package bot.wuliang.utils

import bot.wuliang.botLog.logUtil.LoggerUtils.logError
import bot.wuliang.config.WARFRAME_BASE_PUBLIC_EXPORT
import bot.wuliang.config.WARFRAME_BASE_PUBLIC_EXPORT_BACKUP
import bot.wuliang.config.WfLexiconConfig.WF_LEXICON_ENDPOINTS_KEY
import bot.wuliang.httpUtil.HttpUtil
import bot.wuliang.redis.RedisService
import bot.wuliang.utils.LZMAUtil.lzmaDecompress
import bot.wuliang.utils.WfLexiconUtil.EndpointResult
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit

/**
 * 检索解析过程中所需的基础项数据
 */
@Component
class WfScraper {
    @Autowired
    private lateinit var redisService: RedisService

    private var originServerAvailable: Boolean = true

    fun checkOriginServerAvailability(): Boolean {
        originServerAvailable = try {
            fetchEndpoints(manifest = true, locale = "en")
            true
        } catch (e: Exception) {
            logError("检查Warframe接口可用性时出错: ${e.message}")
            false
        }
        return originServerAvailable
    }

    /**
     * 从Warframe的接口获取API清单列表
     * @param manifest 是否仅获取清单
     * @param locale 语言
     */
    fun fetchEndpoints(manifest: Boolean = false, locale: String = "en"): EndpointResult {
        return retryAttempts(5) {

            val origin = if (originServerAvailable) {
                "$WARFRAME_BASE_PUBLIC_EXPORT${locale}.txt.lzma"
            } else {
                "$WARFRAME_BASE_PUBLIC_EXPORT_BACKUP${locale}.txt.lzma"
            }

            var raw = redisService.getValue("$WF_LEXICON_ENDPOINTS_KEY$origin") as? ByteArray
            if (raw == null) {
                raw =
                    HttpUtil.doGetBytes(
                        url = origin,
                        headers = mutableMapOf("user-agent" to "node-fetch (warframe-items)")
                    )
            }
            val decompressed = lzmaDecompress(raw).decodeToString()
            redisService.setValueWithExpiry(
                "$WF_LEXICON_ENDPOINTS_KEY$origin",
                raw,
                30L,
                TimeUnit.DAYS
            ) // 当解压成功时，缓存压缩数据

            val manifestRegex = Regex("(\r?\n)?ExportManifest.*")
            if (manifest) {
                return@retryAttempts EndpointResult.Manifest(
                    manifestRegex.find(decompressed)?.value?.replace(
                        Regex("\r?\n"),
                        ""
                    ) ?: ""
                )
            }
            return@retryAttempts EndpointResult.Endpoints(decompressed.replace(manifestRegex, "").split(Regex("\r?\n")))
        }
    }


    /**
     * 重试指定次数，直到成功或抛出异常
     * @param attempts 重试次数
     * @param block 要执行的代码块
     */
    fun <T> retryAttempts(attempts: Int, block: () -> T): T {
        var lastException: Exception? = null
        repeat(attempts) {
            try {
                return block()
            } catch (e: Exception) {
                lastException = e
            }
        }
        throw lastException ?: Exception("未知错误")
    }
}