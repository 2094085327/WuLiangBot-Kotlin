package bot.demo.txbot.other

import java.io.UnsupportedEncodingException
import java.net.URLDecoder
import java.net.URLEncoder
import java.util.*
import javax.servlet.http.Cookie
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse


/**
 * @description: Cookie工具类
 * @author Nature Zero
 * @date 2024/10/11 22:39
 */
@Suppress("unused")
object CookieUtil {
    /**
     * 得到Cookie的值, 不编码
     *
     * @param request
     * @param cookieName
     * @return
     */
    fun getCookieValue(request: HttpServletRequest, cookieName: String?): String? {
        return getCookieValue(request, cookieName, false)
    }

    /**
     * 得到Cookie的值,
     *
     * @param request
     * @param cookieName
     * @return
     */
    fun getCookieValue(request: HttpServletRequest, cookieName: String?, isDecoder: Boolean): String? {
        val cookieList = request.cookies
        if (cookieList == null || cookieName == null) {
            return null
        }
        var retValue: String? = null
        try {
            for (i in cookieList.indices) {
                if (cookieList[i].name == cookieName) {
                    retValue = if (isDecoder) {
                        URLDecoder.decode(cookieList[i].value, "UTF-8")
                    } else {
                        cookieList[i].value
                    }
                    break
                }
            }
        } catch (e: UnsupportedEncodingException) {
            e.printStackTrace()
        }
        return retValue
    }

    /**
     * 得到Cookie的值,
     *
     * @param request
     * @param cookieName
     * @return
     */
    fun getCookieValue(request: HttpServletRequest, cookieName: String?, encodeString: String): String? {
        val cookieList = request.cookies
        if (cookieList == null || cookieName == null) {
            return null
        }
        var retValue: String? = null
        try {
            for (i in cookieList.indices) {
                if (cookieList[i].name == cookieName) {
                    retValue = URLDecoder.decode(cookieList[i].value, encodeString)
                    break
                }
            }
        } catch (e: UnsupportedEncodingException) {
            e.printStackTrace()
        }
        return retValue
    }

    /**
     * 设置Cookie的值 不设置生效时间默认浏览器关闭即失效,也不编码
     */
    fun setCookie(
        request: HttpServletRequest?, response: HttpServletResponse, cookieName: String,
        cookieValue: String?
    ) {
        setCookie(request, response, cookieName, cookieValue, -1)
    }

    /**
     * 设置Cookie的值 在指定时间内生效,但不编码
     */
    fun setCookie(
        request: HttpServletRequest?, response: HttpServletResponse, cookieName: String,
        cookieValue: String?, cookieMaxAge: Int
    ) {
        setCookie(request, response, cookieName, cookieValue, cookieMaxAge, false)
    }

    /**
     * 设置Cookie的值 不设置生效时间,但编码
     */
    fun setCookie(
        request: HttpServletRequest?, response: HttpServletResponse, cookieName: String,
        cookieValue: String?, isEncode: Boolean
    ) {
        setCookie(request, response, cookieName, cookieValue, -1, isEncode)
    }

    /**
     * 设置Cookie的值 在指定时间内生效, 编码参数
     */
    fun setCookie(
        request: HttpServletRequest?, response: HttpServletResponse, cookieName: String,
        cookieValue: String?, cookieMaxage: Int, isEncode: Boolean
    ) {
        doSetCookie(
            request,
            response,
            cookieName,
            cookieValue,
            cookieMaxage,
            isEncode
        )
    }

    /**
     * 设置Cookie的值 在指定时间内生效, 编码参数(指定编码)
     */
    fun setCookie(
        request: HttpServletRequest?, response: HttpServletResponse, cookieName: String,
        cookieValue: String?, cookieMaxAge: Int, encodeString: String
    ) {
        doSetCookie(
            request,
            response,
            cookieName,
            cookieValue,
            cookieMaxAge,
            encodeString
        )
    }

    /**
     * 删除Cookie带cookie域名
     */
    fun deleteCookie(
        request: HttpServletRequest?, response: HttpServletResponse,
        cookieName: String
    ) {
        doSetCookie(request, response, cookieName, "", -1, false)
    }

    /**
     * 设置Cookie的值，并使其在指定时间内生效
     *
     * @param cookieMaxage cookie生效的最大秒数
     */
    private fun doSetCookie(
        request: HttpServletRequest?, response: HttpServletResponse,
        cookieName: String, cookieValue: String?, cookieMaxage: Int, isEncode: Boolean
    ) {
        var thisCookieValue = cookieValue
        try {
            if (thisCookieValue == null) {
                thisCookieValue = ""
            } else if (isEncode) {
                thisCookieValue = URLEncoder.encode(thisCookieValue, "utf-8")
            }
            val cookie = Cookie(cookieName, thisCookieValue)
            if (cookieMaxage > 0) cookie.maxAge = cookieMaxage
            if (null != request) { // 设置域名的cookie
                val domainName: String = getDomainName(request)
                if ("localhost" != domainName) {
                    cookie.domain = domainName
                }
            }
            cookie.path = "/"
            response.addCookie(cookie)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 设置Cookie的值，并使其在指定时间内生效
     *
     * @param cookieMaxage cookie生效的最大秒数
     */
    private fun doSetCookie(
        request: HttpServletRequest?, response: HttpServletResponse,
        cookieName: String, cookieValue: String?, cookieMaxage: Int, encodeString: String
    ) {
        var thisCookieValue = cookieValue
        try {
            thisCookieValue = if (thisCookieValue == null) {
                ""
            } else {
                URLEncoder.encode(thisCookieValue, encodeString)
            }
            val cookie = Cookie(cookieName, thisCookieValue)
            if (cookieMaxage > 0) {
                cookie.maxAge = cookieMaxage
            }
            if (null != request) { // 设置域名的cookie
                val domainName: String = getDomainName(request)
                println(domainName)
                if ("localhost" != domainName) {
                    cookie.domain = domainName
                }
            }
            cookie.path = "/"
            response.addCookie(cookie)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 得到cookie的域名
     */
    private fun getDomainName(request: HttpServletRequest): String {
        var domainName: String?
        // 通过request对象获取访问的url地址
        var serverName = request.requestURL.toString()
        if (serverName == "") {
            domainName = ""
        } else {
            // 将url地下转换为小写
            serverName = serverName.lowercase(Locale.getDefault())
            // 如果url地址是以http://开头  将http://截取
            if (serverName.startsWith("http://")) {
                serverName = serverName.substring(7)
            }
            var end = serverName.length
            // 判断url地址是否包含"/"
            if (serverName.contains("/")) {
                //得到第一个"/"出现的位置
                end = serverName.indexOf("/")
            }

            // 截取
            serverName = serverName.substring(0, end)
            // 根据"."进行分割
            val domains = serverName.split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            val len = domains.size
            domainName = if (len > 3) {
                // www.xxx.com.cn
                domains[len - 3] + "." + domains[len - 2] + "." + domains[len - 1]
            } else if (len in 2..3) {
                // xxx.com or xxx.cn
                domains[len - 2] + "." + domains[len - 1]
            } else {
                serverName
            }
        }

        if (domainName.indexOf(":") > 0) {
            val ary = domainName.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            domainName = ary[0]
        }
        return domainName
    }
}