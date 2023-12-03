package bot.demo.txbot.common.utils

import cn.hutool.extra.spring.SpringUtil
import com.fasterxml.jackson.databind.JsonNode
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import org.apache.hc.client5.http.classic.methods.HttpGet
import org.apache.hc.client5.http.classic.methods.HttpPost
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.apache.hc.core5.http.*
import org.apache.hc.core5.http.io.entity.EntityUtils
import org.apache.hc.core5.net.URIBuilder
import pers.wuliang.robot.common.utils.JacksonUtil
import pers.wuliang.robot.common.utils.LoggerUtils.logInfo
import java.io.File
import java.io.IOException
import java.net.URI
import java.net.URLEncoder


/**
 * @author zsck
 * @date   2023/1/26 - 20:04
 */

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

//    @Throws(IOException::class)
//    fun doGetStr(
//        url: String,
//        header: MutableMap<String, Any>? = null,
//        params: Map<String, Any>? = null,
//        isDefault: Boolean = false
//    ): String {
//        val httpGet = HttpGet(getUri(url, params))
//        header?.map { (key, value) -> httpGet.setHeader(key, value.toString()) }
//        return doHttpRequestStr(httpGet, isDefault)
//    }

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

//    @Throws(IOException::class)
//    fun doPostStr(
//        url: String,
//        entity: HttpEntity? = null,
//        header: MutableMap<String, Any>? = null,
//        isDefault: Boolean = false
//    ): String {
//        val httpPost = HttpPost(url)
//        header?.map { (key, value) -> httpPost.setHeader(key, value.toString()) }
//        entity?.let { httpPost.entity = it }
//        return doHttpRequestStr(httpPost, isDefault)
//    }

//    @Throws(IOException::class)
//    fun doGetJson(
//        url: String,
//        header: MutableMap<String, Any>? = null,
//        params: Map<String, Any>? = null,
//        isDefault: Boolean = false
//    ): JsonNode {
//        return JacksonUtil.readTree(doGetStr(url, header, params, isDefault))
//    }

//    @Throws(IOException::class)
//    fun doPostJson(
//        url: String,
//        entity: HttpEntity? = null,
//        header: MutableMap<String, Any>? = null,
//        isDefault: Boolean = false
//    ): JsonNode {
//        return JacksonUtil.readTree(doPostStr(url, entity, header, isDefault))
//    }

    private fun doHttpRequestStr(httpRequestBase: HttpUriRequestBase, isDefault: Boolean = false): String {
        val httpClient: CloseableHttpClient = if (isDefault) this.httpClient else HttpClientBuilder.create().build()
        try {
            httpClient.execute(httpRequestBase).use { exec -> return EntityUtils.toString(exec.entity) }
        } finally {//若不使用默认httpClient则自动关闭
            if (!isDefault) {
                httpClient.close()
            }
        }
    }


    private fun doHttpRequestBytes(httpRequestBase: HttpUriRequestBase, isDefault: Boolean = false): ByteArray {
        val httpClient: CloseableHttpClient = if (isDefault) this.httpClient else HttpClients.createDefault()
        try {
            httpClient.execute(httpRequestBase).use { exec -> return EntityUtils.toByteArray(exec.entity) }
        } finally {//若不使用默认httpClient则自动关闭
            if (!isDefault) {
                httpClient.close()
            }
        }
    }

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

    private fun buildUrlWithParams(url: String, params: Map<String, Any>): String {
        val queryString = params.entries.joinToString("&") { "${it.key}=${it.value}" }
        return if (url.contains("?")) {
            "$url&$queryString"
        } else {
            "$url?$queryString"
        }
    }

    @Throws(IOException::class)
    fun doGetStr(
        url: String,
        params: Map<String, Any>? = null,
        headers: MutableMap<String, Any>? = null,
    ): String {
        val fullUrl = if (params != null) {
            buildUrlWithParams(url, params)
        } else {
            url
        }
        val requestBuilder = Request.Builder()
            .url(fullUrl)
            .get()

        headers?.forEach { (key, value) ->
            requestBuilder.addHeader(key, value.toString())
        }
        val request = requestBuilder.build()

        val response = client.newCall(request).execute()

        return response.use {
            if (it.isSuccessful) {
                it.body.string()
            } else {
                val errorResponse = it.body.string()
                println("Get请求失败: ${it.code}")
                throw HttpException(it.code, errorResponse)
            }
        }
    }

    @Throws(IOException::class)
    fun doGetJson(
        url: String,
        headers: MutableMap<String, Any>? = null,
        params: Map<String, Any>? = null,
    ): JsonNode {
        return JacksonUtil.readTree(doGetStr(url, params, headers))
    }

    /**
     * url:
     */
    @Throws(IOException::class, HttpException::class)
    fun doPostStr(
        url: String,
        files: Map<String, File>? = null,
        params: Map<String, String>? = null,
        headers: MutableMap<String, Any>? = null
    ): String {
        val body = MultipartBody.Builder()

        files?.forEach { (key, file) ->
            body.setType(MultipartBody.FORM)
                .addFormDataPart(key, file.name, file.asRequestBody("application/octet-stream".toMediaType()))
        }
        params?.forEach { (key, value) -> body.addFormDataPart(key, value) }
        val multipartBody = body.build()

        val requestBuilder = Request.Builder()
            .url(url)
            .post(multipartBody)

        headers?.forEach { (key, value) ->
            requestBuilder.addHeader(key, value.toString())
        }
        val request = requestBuilder.build()

        val response = client.newCall(request).execute()

        return response.use {
            if (it.isSuccessful) {
                it.body.string()
            } else {
                val errorResponse = it.body.string()
                println("Post请求失败: ${it.code}")
                throw HttpException(it.code, errorResponse)
            }
        }
    }

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
    class HttpException(val statusCode: Int, val responseBody: String) :
        Exception("HTTP Request Failed: $statusCode, Response: $responseBody")

//    /**
//     * 待子类重写
//     */
//    open fun getHeader(): Header{
//        return null
//    }
//
//    open fun isDefault(): Boolean{
//        return true
//    }
}

@Suppress("unused", "MemberVisibilityCanBePrivate")
object HttpProxy : HttpBase() {


    override lateinit var httpClient: CloseableHttpClient

    val port: String? = SpringUtil.getProperty("zsck.proxy.port")

    val proxyAddress: String? = SpringUtil.getProperty("zsck.proxy.addr")

    init {

        if (port == null || proxyAddress == null) {
            logInfo("HttpProxy init failed, port or proxyAddress is null")
            httpClient = HttpClients.createDefault()
        } else {
            logInfo("HttpProxy init proxyAddress: {}, port: {}", proxyAddress, port)


            // 使用代理
            val httpHost = HttpHost(proxyAddress, port.toInt())

            httpClient = HttpClients.custom().setProxy(httpHost).build()

            logInfo("HttpProxy init success")
        }

    }

}

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


