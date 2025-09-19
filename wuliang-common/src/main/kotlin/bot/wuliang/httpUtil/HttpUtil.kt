package bot.wuliang.httpUtil

import bot.wuliang.botLog.logUtil.LoggerUtils.logError
import bot.wuliang.jacksonUtil.JacksonUtil
import com.fasterxml.jackson.databind.JsonNode
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
import java.net.Proxy
import java.net.URLEncoder
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSession
import javax.net.ssl.X509TrustManager

/**
 * 公共的http请求工具类
 */
object HttpUtil {

    private val client = OkHttpClient()

    private val proxyClients = mutableMapOf<Proxy, OkHttpClient>()

    private fun getClientWithProxy(proxy: Proxy): OkHttpClient {
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
                .hostnameVerifier { _: String, _: SSLSession -> true }
                .proxy(proxy)
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(10, TimeUnit.SECONDS)
                .callTimeout(90, TimeUnit.SECONDS)
                .build()
        }
    }

    /**
     * 对字符串进行URL编码
     *
     * @param charset 字符集，默认为UTF-8
     * @return URL编码后的字符串
     */
    fun String.urlEncode(charset: String = "UTF-8"): String {
        return URLEncoder.encode(this, charset)
    }


    private fun buildRequest(
        url: String,
        method: String,
        body: RequestBody? = null,
        headers: MutableMap<String, Any>? = null
    ): Request {
        val requestBuilder = Request.Builder().url(url).method(method, body)
        headers?.forEach { (key, value) ->
            requestBuilder.addHeader(key, value.toString())
        }
        return requestBuilder.build()
    }

    /**
     * 构建带参URL
     *
     * @param baseUrl 基础URL
     * @param params 参数映射表
     * @return 带参URL
     */
    private fun buildUrlWithParams(baseUrl: String, params: Map<String, Any>?): String {
        if (params.isNullOrEmpty()) return baseUrl

        val queryString = params.entries.joinToString("&") { (key, value) ->
            "${key.urlEncode()}=${value.toString().urlEncode()}"
        }

        return if (baseUrl.contains("?")) "$baseUrl&$queryString" else "$baseUrl?$queryString"
    }

    /**
     * 提取通用的执行逻辑
     *
     * @param url 请求链接
     * @param body 请求体
     * @param headers 请求头
     */
    private fun executeRequest(
        url: String,
        body: RequestBody,
        headers: MutableMap<String, Any>? = null
    ): String {
        val request = buildRequest(url, "POST", body = body, headers = headers)
        val response = client.newCall(request).execute()

        return response.use {
            if (it.isSuccessful) it.body.string()
            else {
                val errorResponse = it.body.string()
                logError("Post请求失败: ${it.code}, URL: $url")
                throw HttpException(it.code, errorResponse)
            }
        }
    }

    /**
     * 发送GET请求并返回字节数组
     *
     * @param url 请求地址
     * @param params 请求参数
     * @param headers 请求头
     * @param proxy 代理设置
     * @return 响应的字节数组
     * @throws IOException 网络IO异常
     */
    @Throws(IOException::class)
    fun doGetBytes(
        url: String,
        params: Map<String, Any>? = null,
        headers: MutableMap<String, Any>? = null,
        proxy: Proxy? = null
    ): ByteArray {
        val fullUrl = if (params != null) buildUrlWithParams(url, params) else url
        val clientToUse = proxy?.let { getClientWithProxy(it) } ?: client

        val request = buildRequest(fullUrl, "GET", headers = headers)
        val response = clientToUse.newCall(request).execute()

        return response.use {
            if (it.isSuccessful) it.body.bytes()
            else throw HttpException(it.code, it.body.string())
        }
    }


    /**
     * 发送GET请求并返回字符串
     *
     * @param url 请求链接
     * @param params 请求参数
     * @param headers 请求头
     * @param proxy 代理设置
     * @param logErrors 是否记录错误日志
     * @return 响应的字符串内容
     * @throws IOException 网络IO异常
     */
    @Throws(IOException::class)
    fun doGetStr(
        url: String,
        params: Map<String, Any>? = null,
        headers: MutableMap<String, Any>? = null,
        proxy: Proxy? = null,
        logErrors: Boolean = true
    ): String {
        val fullUrl = if (params != null) buildUrlWithParams(url, params) else url
        val clientToUse = if (proxy == null) client else getClientWithProxy(proxy)

        val request = buildRequest(fullUrl, "GET", headers = headers)
        return try {
            val response = clientToUse.newCall(request).execute()
            response.use {
                if (it.isSuccessful) {
                    it.body.string()
                } else {
                    val errorResponse = it.body.string()
                    throw HttpException(it.code, errorResponse)
                }
            }
        } catch (e: Exception) {
            if (logErrors) {
                logError("请求执行异常: ${e.message}, URL: ${request.url}, 代理: $proxy")
            }
            throw e
        }
    }


    /**
     * 发送GET请求并返回JSON节点
     *
     * @param url 请求链接
     * @param headers 请求头
     * @param params 请求参数
     * @param proxy 代理设置
     * @param logErrors 是否记录错误日志
     * @return 响应的JSON节点
     * @throws IOException 网络IO异常
     */
    @Throws(IOException::class)
    fun doGetJson(
        url: String,
        headers: MutableMap<String, Any>? = null,
        params: Map<String, Any>? = null,
        proxy: Proxy? = null,
        logErrors: Boolean = true
    ): JsonNode {
        return JacksonUtil.readTree(doGetStr(url, params, headers, proxy, logErrors))
    }


    /**
     * 发送POST请求
     *
     * @param url 请求链接
     * @param jsonBody JSON格式的请求体
     * @param files 要上传的文件映射表
     * @param params 表单参数
     * @param headers 请求头
     * @return 响应的字符串内容
     * @throws IOException 网络IO异常
     * @throws HttpException HTTP请求异常
     */
    @Throws(IOException::class, HttpException::class)
    fun doPost(
        url: String,
        jsonBody: String? = null,
        files: Map<String, File>? = null,
        params: Map<String, String>? = null,
        headers: MutableMap<String, Any>? = null
    ): String {
        val bodyBuilder = MultipartBody.Builder().setType(MultipartBody.FORM)

        // 添加 JSON 或表单参数
        jsonBody?.let {
            val requestBody = it.toRequestBody("application/json; charset=utf-8".toMediaType())
            return executeRequest(url, requestBody, headers)
        }

        // 添加文件和表单参数
        files?.forEach { (key, file) ->
            val fileBody = file.asRequestBody("multipart/form-data".toMediaType())
            bodyBuilder.addFormDataPart(key, file.name, fileBody)
        }
        params?.forEach { (key, value) ->
            bodyBuilder.addFormDataPart(key, value)
        }

        return executeRequest(url, bodyBuilder.build(), headers)
    }


    /**
     * 发送带有文件的POST请求并以JSON节点返回
     *
     * @param url 请求链接
     * @param jsonBody JSON格式的请求体
     * @param files 文件映射表
     * @param params 参数映射表
     * @param headers 请求头
     * @return 响应的JSON节点
     * @throws IOException 网络IO异常
     * @throws HttpException HTTP请求异常
     */
    @Throws(IOException::class, HttpException::class)
    fun doPostJson(
        url: String,
        jsonBody: String? = null,
        files: Map<String, File>? = null,
        params: Map<String, String>? = null,
        headers: MutableMap<String, Any>? = null
    ): JsonNode {
        return JacksonUtil.readTree(doPost(url, jsonBody, files, params, headers))
    }

    /**
     * HTTP请求异常类
     *
     * @param statusCode HTTP状态码
     * @param responseBody 响应体内容
     * @constructor 创建一个HTTP异常实例
     */
    class HttpException(statusCode: Int, responseBody: String) :
        Exception("HTTP Request Failed: $statusCode, Response: $responseBody")
}
