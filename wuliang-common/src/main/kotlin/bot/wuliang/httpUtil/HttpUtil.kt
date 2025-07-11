package bot.wuliang.httpUtil

import bot.wuliang.botLog.logUtil.LoggerUtils.logError
import bot.wuliang.botLog.logUtil.LoggerUtils.logInfo
import bot.wuliang.httpUtil.HttpBase.UrlUtil.urlEncode
import bot.wuliang.jacksonUtil.JacksonUtil
import com.fasterxml.jackson.databind.JsonNode
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.apache.hc.client5.http.classic.methods.HttpGet
import org.apache.hc.client5.http.classic.methods.HttpPost
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.apache.hc.core5.http.*
import org.apache.hc.core5.http.io.HttpClientResponseHandler
import org.apache.hc.core5.http.io.entity.EntityUtils
import org.apache.hc.core5.net.URIBuilder
import java.io.File
import java.io.IOException
import java.net.Proxy
import java.net.URI
import java.net.URLEncoder
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager


/**
 * 公共的http请求工具类
 */
@Suppress("unused")
object HttpUtil : HttpBase() {
    fun isDefault(): Boolean {
        return false
    }
}

@Suppress("unused")
open class HttpBase {

    protected open val httpClient: CloseableHttpClient = HttpClients.createDefault()

    protected open val client = OkHttpClient()

    companion object {
        private val proxyClients = mutableMapOf<Proxy, OkHttpClient>()

        fun getClientWithProxy(proxy: Proxy): OkHttpClient {
            val trustManager = object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            }

            val sslContext = SSLContext.getInstance("SSL").apply {
                init(null, arrayOf(trustManager), null)
            }

            return proxyClients.getOrPut(proxy) {
                OkHttpClient.Builder()
                    .sslSocketFactory(sslContext.socketFactory, trustManager)
                    .hostnameVerifier { _, _ -> true }
                    .proxy(proxy)
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(60, TimeUnit.SECONDS)
                    .writeTimeout(10, TimeUnit.SECONDS)
                    .callTimeout(90, TimeUnit.SECONDS)
                    .build()
            }
        }
    }

    /**
     * 发送get请求
     *
     * @param url 请求地址
     * @param header 请求头
     * @param params 请求参数
     * @param isDefault 是否使用默认httpClient
     * @return 请求结果
     */
    @Throws(IOException::class)
    fun doGetBytes(
        url: String,
        header: Header,
        params: Map<String, Any>? = null,
        isDefault: Boolean = false
    ): ByteArray {
        val httpGet = HttpGet(getUri(url, params))
        httpGet.setHeaders(header)
        return doHttpRequestBytes(httpGet, isDefault)
    }

    /**
     * 发送get请求
     *
     * @param httpRequestBase 请求
     * @param isDefault 是否使用默认httpClient
     * @return 请求结果
     */
    private fun doHttpRequestStr(httpRequestBase: ClassicHttpRequest, isDefault: Boolean = false): String {
        val httpClient: CloseableHttpClient = if (isDefault) this.httpClient else HttpClients.createDefault()
        return try {
            val responseHandler = HttpClientResponseHandler { response ->
                val entity = response.entity
                if (entity != null) {
                    EntityUtils.toString(entity)
                } else throw IOException("No response entity")
            }
            httpClient.execute(httpRequestBase, responseHandler)
        } finally {
            if (!isDefault) {
                httpClient.close()
            }
        }
    }


    /**
     * 发送get请求
     *
     * @param httpRequestBase 请求
     * @param isDefault 是否使用默认httpClient
     * @return 请求结果
     */
    private fun doHttpRequestBytes(httpRequestBase: ClassicHttpRequest, isDefault: Boolean = false): ByteArray {
        val httpClient: CloseableHttpClient = if (isDefault) this.httpClient else HttpClients.createDefault()
        return try {
            val responseHandler = HttpClientResponseHandler { response ->
                val entity = response.entity
                if (entity != null) {
                    EntityUtils.toByteArray(entity)
                } else throw IOException("No response entity")
            }
            httpClient.execute(httpRequestBase, responseHandler)
        } finally {
            if (!isDefault) {
                httpClient.close()
            }
        }
    }

    /**
     * 编码
     *
     * @param url 请求地址
     * @param params 参数
     * @return uri
     */
    private fun getUri(url: String, params: Map<String, Any>?): String {
        val uri = URI(url)
        val encodedUrl = uri.toASCIIString()
        val builder = URIBuilder(encodedUrl)
        if (params != null) {
            for ((key, value) in params) {
                builder.addParameter(key, value as String?)
            }
        }
        return builder.build().toString()
    }

    /**
     * 构建带参URL
     *
     * @param url 根URL
     * @param params 参数
     * @return 带参URL
     */
    private fun buildUrlWithParams(url: String, params: Map<String, Any>): String {
        val queryString = params.entries.joinToString("&") {
            "${it.key.urlEncode()}=${it.value.toString().urlEncode()}"
        }
        return if (url.contains("?")) "$url&$queryString" else "$url?$queryString"
    }

    /**
     * 发送StrGet请求
     *
     * @param url 请求链接
     * @param params 请求参数
     * @param headers 请求头
     * @return 请求结果
     */
    @Throws(IOException::class)
    fun doGetStr(
        url: String,
        params: Map<String, Any>? = null,
        headers: MutableMap<String, Any>? = null,
        proxy: Proxy? = null
    ): String {
        val fullUrl = if (params != null) buildUrlWithParams(url, params) else url

        val clientToUse = if (proxy == null) client else getClientWithProxy(proxy)

        val requestBuilder = Request.Builder().url(fullUrl).get()
        headers?.forEach { (key, value) ->
            requestBuilder.addHeader(key, value.toString())
        }

        val request = requestBuilder.build()

        var call: Call? = null
        var response: Response? = null
        try {
            call = clientToUse.newCall(request)
            response = call.execute()

            if (response.isSuccessful) {
                val body = response.body.string()
                return body
            } else {
                val errorResponse = response.body.string()
                throw HttpException(response.code, errorResponse)
            }
        } catch (e: Exception) {
            call?.cancel()
            logError("请求执行异常: ${e.message}, URL: ${request.url}, 代理: $proxy")
            throw e
        } finally {
            // 确保关闭连接
            response?.close()
        }
    }
    @Throws(IOException::class)
    fun doGetStrNoLog(
        url: String,
        params: Map<String, Any>? = null,
        headers: MutableMap<String, Any>? = null,
        proxy: Proxy? = null
    ): String {
        val fullUrl = if (params != null) buildUrlWithParams(url, params) else url

        val clientToUse = if (proxy == null) client else getClientWithProxy(proxy)

        val requestBuilder = Request.Builder()
            .url(fullUrl)
            .get()

        headers?.forEach { (key, value) ->
            requestBuilder.addHeader(key, value.toString())
        }
        val request = requestBuilder.build()

        val response = clientToUse.newCall(request).execute()

        return response.use {
            if (it.isSuccessful) {
                it.body.string()
            } else {
                val errorResponse = it.body.string()
                throw HttpException(it.code, errorResponse)
            }
        }
    }


    /**
     * 发送JsonGet请求
     *
     * @param url 请求链接
     * @param headers 请求头
     * @param params 请求参数
     * @return 请求结果
     */
    @Throws(IOException::class)
    fun doGetJson(
        url: String,
        headers: MutableMap<String, Any>? = null,
        params: Map<String, Any>? = null,
        proxy: Proxy? = null
    ): JsonNode {
        return JacksonUtil.readTree(doGetStr(url, params, headers,proxy))
    }

    /**
     * 发送不显示错误日志的JsonGet请求
     *
     * @param url 请求链接
     * @param headers 请求头
     * @param params 请求参数
     * @return 请求结果
     */
    @Throws(IOException::class)
    fun doGetJsonNoLog(
        url: String,
        headers: MutableMap<String, Any>? = null,
        params: Map<String, Any>? = null,
    ): JsonNode {
        return JacksonUtil.readTree(doGetStrNoLog(url, params, headers))
    }

    /**
     * 发送带有文件的Post请求
     *
     * @param url 请求链接
     * @param files 文件
     * @param params 参数
     * @param headers 请求头
     * @return 请求结果
     */
    @Throws(IOException::class, HttpException::class)
    fun doPostStr(
        url: String,
        files: Map<String, File>? = null,
        params: Map<String, String>? = null,
        headers: MutableMap<String, Any>? = null
    ): String {
        // 创建 MultipartBody.Builder，并设置类型为 FORM
        val bodyBuilder = MultipartBody.Builder().setType(MultipartBody.FORM)


        // 添加文件部分
        files?.forEach { (key, file) ->
            val fileBody: RequestBody = file.asRequestBody("multipart/form-data".toMediaType())

            bodyBuilder.addFormDataPart(key, file.name, fileBody)
        }

        // 添加其他表单参数
        params?.forEach { (key, value) ->
            bodyBuilder.addFormDataPart(key, value)
        }

        // 构建最终的 MultipartBody
        val multipartBody = bodyBuilder.build()

        // 创建请求构建器
        val requestBuilder = Request.Builder()
            .url(url)
            .post(multipartBody)

        // 添加请求头
        headers?.forEach { (key, value) ->
            requestBuilder.addHeader(key, value.toString())
        }

        // 构建请求
        val request = requestBuilder.build()
        val response = client.newCall(request).execute()

        return response.use {
            if (it.isSuccessful) {
                it.body.string()
            } else {
                val errorResponse = it.body.string()
                logError("Post请求失败: ${it.code} $request")
                throw HttpException(it.code, errorResponse)
            }
        }
    }

    /**
     * 发送带有Json请求体的Post请求
     *
     * @param url 请求链接
     * @param jsonBody 请求体
     * @param headers 请求头
     * @return 请求结果
     */
    @Throws(IOException::class, HttpException::class)
    fun doPostJson(
        url: String,
        jsonBody: String? = null,
        headers: MutableMap<String, Any>? = null
    ): JsonNode {
        val mediaType = "application/json; charset=utf-8".toMediaType()

        val requestBuilder = Request.Builder().url(url)

        jsonBody?.let {
            val requestBody = it.toRequestBody(mediaType)
            requestBuilder.post(requestBody)
        }

        headers?.forEach { (key, value) ->
            requestBuilder.addHeader(key, value.toString())
        }

        val request = requestBuilder.build()

        val response = client.newCall(request).execute()

        return response.use {
            if (it.isSuccessful) JacksonUtil.readTree(it.body.string())
            else {
                val errorResponse = it.body.string()
                logError("Post请求失败: ${it.code} $request")
                throw HttpException(it.code, errorResponse)
            }
        }
    }

    /**
     * 发送带有文件的Post请求并以JsonNode返回
     *
     * @param url 请求链接
     * @param files 文件
     * @param params 参数
     * @param headers 请求头
     * @return 请求结果
     */
    @Throws(IOException::class, HttpException::class)
    fun doPostJson(
        url: String,
        files: Map<String, File>? = null,
        params: Map<String, String>? = null,
        headers: MutableMap<String, Any>? = null
    ): JsonNode {
        return JacksonUtil.readTree(doPostStr(url, files, params, headers))
    }

    // 自定义异常类，表示HTTP请求异常
    class HttpException(statusCode: Int, responseBody: String) :
        Exception("HTTP Request Failed: $statusCode, Response: $responseBody")


    @Suppress("unused")
    object UrlUtil {
        fun String.urlEncode(charset: String = "UTF-8"): String {
            return URLEncoder.encode(this, charset)
        }

        fun String.toHttpGet(): HttpGet {
            return HttpGet(this)
        }

        fun String.toHttpPost(): HttpPost {
            return HttpPost(this)
        }

    }
}

