package bot.wuliang.utils

import bot.wuliang.text.Convert
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes
import java.io.IOException
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import javax.servlet.http.HttpSession

/**
 * 客户端工具类
 */
object ServletUtils {

    /**
     * 获取String参数
     */
    fun getParameter(name: String): String {
        return getRequest().getParameter(name)
    }

    /**
     * 获取String参数
     */
    fun getParameter(name: String, defaultValue: String): String? {
        return Convert.toStr(getRequest().getParameter(name), defaultValue)
    }

    /**
     * 获取Integer参数
     */
    fun getParameterToInt(name: String): Int? {
        return Convert.toInt(getRequest().getParameter(name))
    }

    /**
     * 获取Integer参数
     */
    fun getParameterToInt(name: String, defaultValue: Int?): Int? {
        return Convert.toInt(getRequest().getParameter(name), defaultValue)
    }

    /**
     * 获取Boolean参数
     */
    fun getParameterToBool(name: String): Boolean {
        return Convert.toBool(getRequest().getParameter(name))
    }

    /**
     * 获取Boolean参数
     */
    fun getParameterToBool(name: String, defaultValue: Boolean): Boolean {
        return Convert.toBool(getRequest().getParameter(name), defaultValue)
    }

    /**
     * 获取request
     */
    fun getRequest(): HttpServletRequest {
        return getRequestAttributes().request
    }

    /**
     * 获取response
     */
    fun getResponse(): HttpServletResponse? {
        return getRequestAttributes().response
    }

    /**
     * 获取session
     */
    fun getSession(): HttpSession {
        return getRequest().getSession()
    }

    fun getRequestAttributes(): ServletRequestAttributes {
        val attributes = RequestContextHolder.getRequestAttributes()
        return attributes as ServletRequestAttributes
    }

    /**
     * 将字符串渲染到客户端
     *
     * @param response 渲染对象
     * @param string 待渲染的字符串
     * @return null
     */
    fun renderString(response: HttpServletResponse, string: String): String? {
        try {
            response.status = 200
            response.contentType = "application/json"
            response.characterEncoding = "utf-8"
            response.writer.print(string)
        } catch (e: IOException) {
            e.printStackTrace()
        }
        return null
    }
}
