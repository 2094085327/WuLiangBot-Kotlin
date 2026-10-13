package bot.wuliang.translation

import bot.wuliang.config.PublicExportPolicy
import bot.wuliang.config.WarframeProperties
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** 官方和 GitHub 导出共用连接池、超时及有界下载 */
internal class PublicExportHttpClient(settings: WarframeProperties.ExportHttp) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(settings.connectTimeoutSeconds, TimeUnit.SECONDS)
        .readTimeout(settings.readTimeoutSeconds, TimeUnit.SECONDS)
        .callTimeout(settings.callTimeoutSeconds, TimeUnit.SECONDS)
        .build()

    fun request(url: HttpUrl): Request.Builder = Request.Builder().url(url)
        .header("User-Agent", PublicExportPolicy.USER_AGENT)

    fun get(request: Request, errorMessage: (Int) -> String): ByteArray =
        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { errorMessage(response.code) }
            val body = requireNotNull(response.body) { "导出响应内容为空" }
            require(body.contentLength() <= PublicExportPolicy.MAX_FILE_BYTES) { "导出文件超过允许大小" }
            body.byteStream().use { it.readNBytes(PublicExportPolicy.MAX_FILE_BYTES + 1) }.also {
                require(it.size <= PublicExportPolicy.MAX_FILE_BYTES) { "导出文件超过允许大小" }
            }
        }
}