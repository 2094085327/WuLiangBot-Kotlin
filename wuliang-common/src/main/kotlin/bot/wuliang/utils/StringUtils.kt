package bot.wuliang.utils

import org.springframework.util.AntPathMatcher
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashSet

/**
 * 字符串工具类
 */
object StringUtils : org.apache.commons.lang3.StringUtils() {
    /** 空字符串 */
    private const val NULLSTR = ""

    const val SEPARATOR_NORMAL = ","

    /** 下划线 */
    private const val SEPARATOR = '_'

    const val PAD_LEFT = 1L
    const val PAD_RIGHT = 2L

    /**
     * 获取参数不为空值
     *
     * @param value defaultValue 要判断的value
     * @return value 返回值
     */
    fun <T> nvl(value: T, defaultValue: T): T {
        return value ?: defaultValue
    }

    /**
     * * 判断一个Collection是否为空， 包含List，Set，Queue
     *
     * @param coll 要判断的Collection
     * @return true：为空 false：非空
     */
    fun isEmpty(coll: Collection<*>?): Boolean {
        return coll == null || coll.isEmpty()
    }

    /**
     * * 判断一个Collection是否非空，包含List，Set，Queue
     *
     * @param coll 要判断的Collection
     * @return true：非空 false：空
     */
    fun isNotEmpty(coll: Collection<*>?): Boolean {
        return !isEmpty(coll)
    }

    /**
     * * 判断一个对象数组是否为空
     *
     * @param objects 要判断的对象数组
     * @return true：为空 false：非空
     */
    fun isEmpty(vararg objects: Any?): Boolean {
        return objects.isEmpty()
    }

    /**
     * * 判断一个对象数组是否非空
     *
     * @param objects 要判断的对象数组
     * @return true：非空 false：空
     */
    fun isNotEmpty(vararg objects: Any?): Boolean {
        return !isEmpty(objects)
    }

    /**
     * * 判断一个Map是否为空
     *
     * @param map 要判断的Map
     * @return true：为空 false：非空
     */
    fun isEmpty(map: Map<*, *>?): Boolean {
        return map == null || map.isEmpty()
    }

    /**
     * * 判断一个Map是否为空
     *
     * @param map 要判断的Map
     * @return true：非空 false：空
     */
    fun isNotEmpty(map: Map<*, *>?): Boolean {
        return !isEmpty(map)
    }

    /**
     * * 判断一个字符串是否为空串
     *
     * @param str String
     * @return true：为空 false：非空
     */
    fun isEmpty(str: String?): Boolean {
        return str == null || NULLSTR == str.trim()
    }

    /**
     * * 判断一个字符串是否为非空串
     *
     * @param str String
     * @return true：非空串 false：空串
     */
    fun isNotEmpty(str: String?): Boolean {
        return !isEmpty(str)
    }

    /**
     * * 判断一个对象是否为空
     *
     * @param object Object
     * @return true：为空 false：非空
     */
    fun isNull(`object`: Any?): Boolean {
        return `object` == null
    }

    /**
     * * 判断一个对象是否非空
     *
     * @param object Object
     * @return true：非空 false：空
     */
    fun isNotNull(`object`: Any?): Boolean {
        return !isNull(`object`)
    }

    /**
     * * 判断一个对象是否是数组类型（Java基本型别的数组）
     *
     * @param object 对象
     * @return true：是数组 false：不是数组
     */
    fun isArray(`object`: Any?): Boolean {
        return `object` != null && `object`.javaClass.isArray
    }


    /**
     * 字符串转set
     *
     * @param str 字符串
     * @param sep 分隔符
     * @return set集合
     */
    fun str2Set(str: String, sep: String): Set<String> {
        return HashSet(str2List(str, sep, true, false))
    }

    /**
     * 字符串转list
     *
     * @param str 字符串
     * @param sep 分隔符
     * @param filterBlank 过滤纯空白
     * @param trim 去掉首尾空白
     * @return list集合
     */
    fun str2List(str: String, sep: String, filterBlank: Boolean, trim: Boolean): List<String> {
        val list = ArrayList<String>()
        if (isEmpty(str)) {
            return list
        }

        // 过滤空白字符串
        if (filterBlank && isBlank(str)) {
            return list
        }
        val split = str.split(sep.toRegex())
        for (string in split) {
            if (filterBlank && isBlank(string)) {
                continue
            }
            var modifiedString = string
            if (trim) {
                modifiedString = modifiedString.trim()
            }
            list.add(modifiedString)
        }

        return list
    }

    /**
     * 驼峰转下划线命名
     */
    fun toUnderScoreCase(str: String?): String? {
        if (str == null) {
            return null
        }
        val sb = StringBuilder()
        // 前置字符是否大写
        var preCharIsUpperCase = true
        // 当前字符是否大写
        var curreCharIsUpperCase = true
        // 下一字符是否大写
        var nexteCharIsUpperCase = true
        for (i in str.indices) {
            val c = str[i]
            preCharIsUpperCase = if (i > 0) {
                Character.isUpperCase(str[i - 1])
            } else {
                false
            }

            curreCharIsUpperCase = Character.isUpperCase(c)

            if (i < (str.length - 1)) {
                nexteCharIsUpperCase = Character.isUpperCase(str[i + 1])
            }

            if (preCharIsUpperCase && curreCharIsUpperCase && !nexteCharIsUpperCase) {
                sb.append(SEPARATOR)
            } else if ((i != 0 && !preCharIsUpperCase) && curreCharIsUpperCase) {
                sb.append(SEPARATOR)
            }
            sb.append(Character.toLowerCase(c))
        }

        return sb.toString()
    }

    /**
     * 是否包含字符串
     *
     * @param str 验证字符串
     * @param strs 字符串组
     * @return 包含返回true
     */
    fun inStringIgnoreCase(str: String, vararg strs: String): Boolean {
        if (str.isNotEmpty() && strs.isNotEmpty()) {
            for (s in strs) {
                if (str.equals(trim(s), ignoreCase = true)) {
                    return true
                }
            }
        }
        return false
    }

    /**
     * 将下划线大写方式命名的字符串转换为驼峰式。如果转换前的下划线大写方式命名的字符串为空，则返回空字符串。 例如：HELLO_WORLD->HelloWorld
     *
     * @param name 转换前的下划线大写方式命名的字符串
     * @return 转换后的驼峰式命名的字符串
     */
    fun convertToCamelCase(name: String): String {
        val result = StringBuilder()
        // 快速检查
        if (name.isEmpty()) {
            // 没必要转换
            return ""
        } else if (!name.contains("_")) {
            // 不含下划线，仅将首字母大写
            return name[0].uppercaseChar() + name.substring(1)
        }
        // 用下划线将原始字符串分割
        val camels = name.split("_".toRegex())
        for (camel in camels) {
            // 跳过原始字符串中开头、结尾的下换线或双重下划线
            if (camel.isEmpty()) {
                continue
            }
            // 首字母大写
            result.append(camel[0].uppercaseChar())
            result.append(camel.substring(1).lowercase(Locale.getDefault()))
        }
        return result.toString()
    }

    /**
     * 驼峰式命名法 例如：user_name->userName
     */
    fun toCamelCase(s: String): String {
        if (s.isEmpty()) {
            return s
        }
        val sb = StringBuilder(s.length)
        var upperCase = false
        for (element in s) {

            if (element == SEPARATOR) {
                upperCase = true
            } else if (upperCase) {
                sb.append(Character.toUpperCase(element))
                upperCase = false
            } else {
                sb.append(element)
            }
        }
        return sb.toString()
    }

    /**
     * 查找指定字符串是否匹配指定字符串列表中的任意一个字符串
     *
     * @param str 指定字符串
     * @param strs 需要检查的字符串数组
     * @return 是否匹配
     */
    fun matches(str: String, strs: List<String>): Boolean {
        if (isEmpty(str) || isEmpty(strs)) {
            return false
        }
        for (pattern in strs) {
            if (isMatch(pattern, str)) {
                return true
            }
        }
        return false
    }

    /**
     * 判断url是否与规则配置:
     * ? 表示单个字符;
     * * 表示一层路径内的任意字符串，不可跨层级;
     * ** 表示任意层路径;
     *
     * @param pattern 匹配规则
     * @param url 需要匹配的url
     * @return
     */
    fun isMatch(pattern: String, url: String): Boolean {
        val matcher = AntPathMatcher()
        return matcher.match(pattern, url)
    }

    @Suppress("UNCHECKED_CAST")
    fun <T> cast(obj: Any?): T {
        return obj as T
    }

    fun acquireGetSetString(name: String): String {
        if (isEmpty(name)) {
            return ""
        }
        if (name.length > 1) {
            if (Character.isUpperCase(name[1])) {
                return name
            }
        }
        return capitalize(name)
    }

    /**
     * 判断一个字符串是否全部是空白字符
     *
     * @param str 要检查的字符串
     * @return true：全是空白 false：非全空白
     */
    fun isBlank(str: String?): Boolean {
        if (isEmpty(str)) {
            return true
        }
        for (c in str!!) {
            if (!Character.isWhitespace(c)) {
                return false
            }
        }
        return true
    }

    /**
     * 检查字符串是否以任意给定的子字符串开头
     *
     * @param str 要检查的字符串
     * @param varargs 可能的开头字符串数组
     * @return true：以其中一个字符串开头 false：都不是开头
     */
    fun startsWithAny(str: String, vararg varargs: String): Boolean {
        for (s in varargs) {
            if (str.startsWith(s, ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    /**
     * 将类似 "SeasonDailyKillEnemiesWithCorrosive" 的字符串转换为每个单词以空格分隔的形式，
     * 例如："Season Daily Kill Enemies With Corrosive"
     *
     * @param input 输入字符串
     * @return 转换后的字符串
     */
    fun formatWithSpaces(input: String): String {
        val result = StringBuilder()
        for ((index, char) in input.withIndex()) {
            if (index > 0 && char.isUpperCase()) {
                result.append(' ')
            }
            result.append(char)
        }
        return result.toString()
    }

    /**
     *  将字符串中的空格替换为下划线
     */
    fun String.formatSpacesToUnderline(): String {
        return this.replace(' ', '_')
    }
}
