package bot.wuliang.translation

import bot.wuliang.config.PublicExportPolicy
import bot.wuliang.config.WarframeProperties
import okhttp3.HttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files
import java.nio.file.Path
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

    fun get(request: Request, errorMessage: (Int) -> String): ByteArray = read(request, errorMessage) { it.readBytes() }

    fun download(request: Request, target: Path, errorMessage: (Int) -> String) {
        read(request, errorMessage) { input -> Files.newOutputStream(target).use { input.copyTo(it) } }
    }

    fun <T> read(request: Request, errorMessage: (Int) -> String, consume: (InputStream) -> T): T =
        client.newCall(request).execute().use { response ->
            check(response.isSuccessful) { errorMessage(response.code) }
            val body = requireNotNull(response.body) { "导出响应内容为空" }
            require(body.contentLength() <= PublicExportPolicy.MAX_FILE_BYTES) { "导出文件超过允许大小" }
            body.byteStream().use { raw ->
                val input = LimitedExportInputStream(raw, PublicExportPolicy.MAX_FILE_BYTES.toLong())
                val result = consume(NonClosingExportInputStream(input))
                input.transferTo(OutputStream.nullOutputStream())
                result
            }
        }
}