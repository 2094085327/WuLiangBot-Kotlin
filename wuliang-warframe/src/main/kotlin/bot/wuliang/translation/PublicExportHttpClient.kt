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

    /** 构造访问 [url] 的请求并附上统一 User-Agent，其他来源请求头由调用方补充。 */
    fun request(url: HttpUrl): Request.Builder = Request.Builder().url(url)
        .header("User-Agent", PublicExportPolicy.USER_AGENT)

    /** 执行有界 HTTP 请求并返回响应字节，非成功状态使用 [errorMessage] 生成错误说明。 */
    fun get(request: Request, errorMessage: (Int) -> String): ByteArray = read(request, errorMessage) { it.readBytes() }

    /** 执行有界 HTTP 下载，将响应流写入 [target]，输入与输出流由此方法管理关闭。 */
    fun download(request: Request, target: Path, errorMessage: (Int) -> String) {
        read(request, errorMessage) { input -> Files.newOutputStream(target).use { input.copyTo(it) } }
    }

    /**
     * 执行请求并将有界响应流交给 [consume]，消费后继续读取剩余内容以校验总量。
     *
     * @param request 已配置来源地址及请求头的请求。
     * @param errorMessage 根据非成功 HTTP 状态生成错误说明。
     * @param consume 流式消费操作，其关闭请求不会提前关闭底层校验流。
     * @return 消费操作的返回值。
     */
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