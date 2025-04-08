package bot.wuliang.text

import java.math.BigInteger
import java.text.NumberFormat
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow

@Suppress("unused")
class Convert {
    /**
     * 转换为字符串
     * 如果给定的值为null，或者转换失败，返回默认值
     * 转换失败不会报错
     *
     * @param value 被转换的值
     * @param defaultValue 转换错误时的默认值
     * @return 结果
     */
    fun toStr(value: Any?, defaultValue: String?): String? {
        if (null == value) {
            return defaultValue
        }
        if (value is String) {
            return value
        }
        return value.toString()
    }

    /**
     * 转换为字符串
     * 如果给定的值为null，或者转换失败，返回默认值null
     * 转换失败不会报错
     *
     * @param value 被转换的值
     * @return 结果
     */
    fun toStr(value: Any?): String? {
        return toStr(value, null)
    }

    /**
     * 转换为字符
     * 如果给定的值为null，或者转换失败，返回默认值
     * 转换失败不会报错
     *
     * @param value 被转换的值
     * @param defaultValue 转换错误时的默认值
     * @return 结果
     */
    fun toChar(value: Any?, defaultValue: Char? = null): Char? {
        return when (value) {
            null -> defaultValue // 如果值为 null，返回默认值
            is Char -> value       // 如果值是 Char 类型，直接返回
            else -> {
                val valueStr = toStr(value, null) // 转换为字符串
                if (valueStr.isNullOrEmpty()) defaultValue else valueStr[0] // 取第一个字符或返回默认值
            }
        }
    }

    /**
     * 转换为字符
     * 如果给定的值为null，或者转换失败，返回默认值null
     * 转换失败不会报错
     *
     * @param value 被转换的值
     * @return 结果
     */
    fun toChar(value: Any): Char? {
        return toChar(value, null)
    }

    /**
     * 转换为byte
     * 如果给定的值为null，或者转换失败，返回默认值
     * 转换失败不会报错
     *
     * @param value 被转换的值
     * @param defaultValue 转换错误时的默认值
     * @return 结果
     */
    fun toByte(value: Any?, defaultValue: Byte = 0): Byte {
        return when (value) {
            null -> defaultValue // 如果值为 null，返回默认值
            is Byte -> value     // 如果值是 Byte 类型，直接返回
            is Number -> value.toByte() // 如果是其他数字类型，调用 toByte 转换
            else -> {
                val valueStr = toStr(value, null) // 转换为字符串
                if (valueStr.isNullOrEmpty()) defaultValue // 如果字符串为空，返回默认值
                else valueStr.toByteOrNull() ?: defaultValue // 尝试解析为 Byte，失败则返回默认值
            }
        }
    }


    /**
     * 转换为Short
     * 如果给定的值为null，或者转换失败，返回默认值
     * 转换失败不会报错
     *
     * @param value 被转换的值
     * @param defaultValue 转换错误时的默认值
     * @return 结果
     */
    fun toShort(value: Any?, defaultValue: Short?): Short? {
        if (value == null) return defaultValue
        return when (value) {
            is Short -> value
            is Number -> value.toShort()
            else -> {
                val valueStr = toStr(value, null)
                if (valueStr.isNullOrEmpty()) defaultValue else valueStr.trim().toShortOrNull() ?: defaultValue
            }
        }
    }

    /**
     * 转换为Short
     * 如果给定的值为null，或者转换失败，返回默认值null
     * 转换失败不会报错
     *
     * @param value 被转换的值
     * @return 结果
     */
    fun toShort(value: Any?): Short? = toShort(value, null)

    /**
     * 转换为Number
     * 如果给定的值为空，或者转换失败，返回默认值
     * 转换失败不会报错
     *
     * @param value 被转换的值
     * @param defaultValue 转换错误时的默认值
     * @return 结果
     */
    fun toNumber(value: Any?, defaultValue: Number?): Number? {
        if (value == null) return defaultValue
        if (value is Number) return value
        val valueStr = toStr(value, null)
        if (valueStr.isNullOrEmpty()) return defaultValue
        return try {
            NumberFormat.getInstance().parse(valueStr.trim())
        } catch (e: Exception) {
            defaultValue
        }
    }

    /**
     * 转换为Number
     * 如果给定的值为空，或者转换失败，返回默认值null
     * 转换失败不会报错
     *
     * @param value 被转换的值
     * @return 结果
     */
    fun toNumber(value: Any?): Number? = toNumber(value, null)

    /**
     * 转换为Int
     * 如果给定的值为空，或者转换失败，返回默认值
     * 转换失败不会报错
     *
     * @param value 被转换的值
     * @param defaultValue 转换错误时的默认值
     * @return 结果
     */
    fun toInt(value: Any?, defaultValue: Int?): Int? {
        if (value == null) return defaultValue
        return when (value) {
            is Int -> value
            is Number -> value.toInt()
            else -> {
                val valueStr = toStr(value, null)
                if (valueStr.isNullOrEmpty()) defaultValue else valueStr.trim().toIntOrNull() ?: defaultValue
            }
        }
    }

    /**
     * 转换为Int
     * 如果给定的值为null，或者转换失败，返回默认值null
     * 转换失败不会报错
     *
     * @param value 被转换的值
     * @return 结果
     */
    fun toInt(value: Any?): Int? = toInt(value, null)

    /**
     * 转换为Int数组
     *
     * @param str 被转换的值
     * @return 结果
     */
    fun toIntArray(str: String): Array<Int> = toIntArray(",", str)

    /**
     * 转换为Long数组
     *
     * @param str 被转换的值
     * @return 结果
     */
    fun toLongArray(str: String): Array<Long> = toLongArray(",", str)

    /**
     * 转换为Int数组
     *
     * @param split 分隔符
     * @param str 被转换的值
     * @return 结果
     */
    fun toIntArray(split: String, str: String): Array<Int> {
        if (str.isEmpty()) return emptyArray()
        return str.split(split).mapNotNull { toInt(it, 0) }.toTypedArray()
    }

    /**
     * 转换为Long数组
     *
     * @param split 分隔符
     * @param str 被转换的值
     * @return 结果
     */
    fun toLongArray(split: String, str: String): Array<Long> {
        if (str.isEmpty()) return emptyArray()
        return str.split(split).mapNotNull { toLong(it) }.toTypedArray()
    }

    /**
     * 转换为String数组
     *
     * @param str 被转换的值
     * @return 结果
     */
    fun toStrArray(str: String): Array<String> = toStrArray(",", str)

    /**
     * 转换为String数组
     *
     * @param split 分隔符
     * @param str 被转换的值
     * @return 结果
     */
    fun toStrArray(split: String, str: String): Array<String> = str.split(split).toTypedArray()

    /**
     * 转换为Long
     * 如果给定的值为空，或者转换失败，返回默认值
     * 转换失败不会报错
     *
     * @param value 被转换的值
     * @param defaultValue 转换错误时的默认值
     * @return 结果
     */
    fun toLong(value: Any?, defaultValue: Long?): Long? {
        if (value == null) return defaultValue
        return when (value) {
            is Long -> value
            is Number -> value.toLong()
            else -> {
                val valueStr = toStr(value, null)
                if (valueStr.isNullOrEmpty()) defaultValue else try {
                    valueStr.trim().toBigDecimal().longValueExact()
                } catch (e: Exception) {
                    defaultValue
                }
            }
        }
    }

    /**
     * 转换为Long
     * 如果给定的值为null，或者转换失败，返回默认值null
     * 转换失败不会报错
     *
     * @param value 被转换的值
     * @return 结果
     */
    fun toLong(value: Any?): Long? = toLong(value, null)

    /**
     * 转换为Double
     * 如果给定的值为空，或者转换失败，返回默认值
     * 转换失败不会报错
     *
     * @param value 被转换的值
     * @param defaultValue 转换错误时的默认值
     * @return 结果
     */
    fun toDouble(value: Any?, defaultValue: Double?): Double? {
        if (value == null) return defaultValue
        return when (value) {
            is Double -> value
            is Number -> value.toDouble()
            else -> {
                val valueStr = toStr(value, null)
                if (valueStr.isNullOrEmpty()) defaultValue else try {
                    valueStr.trim().toBigDecimal().toDouble()
                } catch (e: Exception) {
                    defaultValue
                }
            }
        }
    }

    /**
     * 转换为double
     * 如果给定的值为空，或者转换失败，返回默认值null
     * 转换失败不会报错
     *
     * @param value 被转换的值
     * @return 结果
     */
    fun toDouble(value: Any): Double? = toDouble(value, null)

    /**
     * 转换为Float
     * 如果给定的值为空，或者转换失败，返回默认值
     * 转换失败不会报错
     *
     * @param value 被转换的值
     * @param defaultValue 转换错误时的默认值
     * @return 结果
     */
    fun toFloat(value: Any?, defaultValue: Float?): Float? {
        if (value == null) return defaultValue
        return when (value) {
            is Float -> value
            is Number -> value.toFloat()
            else -> {
                val valueStr = toStr(value, null)
                if (valueStr.isNullOrEmpty()) defaultValue else try {
                    valueStr.trim().toFloat()
                } catch (e: Exception) {
                    defaultValue
                }
            }
        }
    }

    /**
     * 转换为Float
     * 如果给定的值为空，或者转换失败，返回默认值null
     * 转换失败不会报错
     *
     * @param value 被转换的值
     * @return 结果
     */
    fun toFloat(value: Any?): Float? = toFloat(value, null)

    /**
     * 转换为Boolean
     * String支持的值为：true、false、yes、ok、no，1,0 如果给定的值为空，或者转换失败，返回默认值
     * 转换失败不会报错
     *
     * @param value 被转换的值
     * @param defaultValue 转换错误时的默认值
     * @return 结果
     */
    fun toBool(value: Any?, defaultValue: Boolean = false): Boolean {
        if (value == null) return defaultValue
        if (value is Boolean) return value

        val valueStr = toStr(value, null)?.trim()?.lowercase() ?: return defaultValue
        return when (valueStr) {
            "true", "yes", "ok", "1" -> true
            "false", "no", "0" -> false
            else -> defaultValue
        }
    }

    /**
     * 转换为Boolean
     * 如果给定的值为空，或者转换失败，返回默认值false
     * 转换失败不会报错
     *
     * @param value 被转换的值
     * @return 结果
     */
    fun toBool(value: Any?): Boolean = toBool(value, false)


    /**
     * 转换为BigInteger
     * 如果给定的值为空，或者转换失败，返回默认值
     * 转换失败不会报错
     *
     * @param value 被转换的值
     * @param defaultValue 转换错误时的默认值
     * @return 结果
     */
    fun toBigInteger(value: Any?, defaultValue: BigInteger?): BigInteger? {
        if (value == null) return defaultValue
        return when (value) {
            is BigInteger -> value
            is Long -> BigInteger.valueOf(value)
            is Number -> BigInteger.valueOf(value.toLong())
            else -> {
                val valueStr = toStr(value, null)?.trim() ?: return defaultValue
                try {
                    BigInteger(valueStr)
                } catch (e: Exception) {
                    defaultValue
                }
            }
        }
    }

    /**
     * 转换为BigInteger
     * 如果给定的值为空，或者转换失败，返回默认值null
     * 转换失败不会报错
     *
     * @param value 被转换的值
     * @return 结果
     */
    fun toBigInteger(value: Any?): BigInteger? = toBigInteger(value, null)


// ---------------------------- 全角半角转换 ----------------------------

    /**
     * 半角转全角
     */
    fun toSBC(input: String?, notConvertSet: Set<Char>? = null): String? {
        if (input.isNullOrEmpty()) return input
        return input.map { c ->
            when {
                notConvertSet?.contains(c) == true -> c
                c == ' ' -> '\u3000'
                c.code < 177 -> (c.code + 65248).toChar()
                else -> c
            }
        }.joinToString("")
    }

    /**
     * 全角转半角
     */
    fun toDBC(text: String?, notConvertSet: Set<Char>? = null): String? {
        if (text.isNullOrEmpty()) return text
        return text.map { c ->
            when {
                notConvertSet?.contains(c) == true -> c
                c == '\u3000' -> ' '
                c in '\uFF00'..'\uFF5F' -> (c.code - 65248).toChar()
                else -> c
            }
        }.joinToString("")
    }

    /**
     * 数字金额大写转换 先写个完整的然后将如零拾替换成零
     *
     * @param n 数字
     * @return 中文大写数字
     */
    fun digitUppercase(n: Double): String {
        val fraction = arrayOf("角", "分")
        val digit = arrayOf("零", "壹", "贰", "叁", "肆", "伍", "陆", "柒", "捌", "玖")
        val unit = arrayOf(
            arrayOf("元", "万", "亿"),
            arrayOf("", "拾", "佰", "仟")
        )

        val head = if (n < 0) "负" else ""
        val num = abs(n)

        var s = ""
        for (i in fraction.indices) {
            val temp = (floor(num * 10 * 10.0.pow(i.toDouble())) % 10).toInt()
            s += digit[temp] + fraction[i]
            s = s.replace(Regex("(零.)+"), "")
        }

        if (s.isEmpty()) {
            s = "整"
        }

        var integerPart = floor(num).toInt()

        for (i in unit[0].indices) {
            var p = ""
            var isZero = true
            for (j in unit[1].indices) {
                if (integerPart <= 0) break
                val current = integerPart % 10
                p = digit[current] + unit[1][j] + p
                integerPart /= 10
                isZero = isZero && current == 0
            }

            val processed = p.replace(Regex("(零.)*零$"), "")
                .replace(Regex("^$"), "零")
            s = if (!isZero) processed + unit[0][i] + s else s
        }

        return head + s.replace(Regex("(零.)*零元"), "元")
            .replace(Regex("(零.)+"), "零")
            .replace(Regex("^整$"), "零元整")
    }
}