package bot.wuliang.jacksonUtil

import bot.wuliang.utils.SpringUtils
import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.type.CollectionType
import java.io.File

/**
 * Jackson工具类，用于JSON序列化和反序列化操作
 *
 * 提供了将对象转换为JSON字符串以及将JSON字符串转换为对象的功能。
 * 支持复杂类型转换、路径提取、集合转换等多种操作。
 */
@Suppress("unused", "MemberVisibilityCanBePrivate")
object JacksonUtil {

    /**
     * ObjectMapper实例，使用懒加载方式初始化
     *
     * 优先尝试从Spring容器中获取ObjectMapper Bean，如果获取失败则创建新的实例
     * 新实例使用SNAKE_CASE命名策略
     */
    val objectMapper: ObjectMapper by lazy {
        try {
            SpringUtils.getBean(ObjectMapper::class.java)
        } catch (e: Exception) {
            ObjectMapper().apply {
                this.propertyNamingStrategy = PropertyNamingStrategies.SNAKE_CASE
            }
        }
    }

    /**
     * 将任意对象转换为JSON字符串
     *
     * @param obj 需要转换的对象，如果已经是字符串则直接返回
     * @return 对象的JSON字符串表示
     */
    fun toJsonString(obj: Any): String {
        if (obj is String) {
            return obj
        }
        return objectMapper.writeValueAsString(obj)
    }

    /**
     * 将JavaScript对象字符串转换为标准JSON格式字符串
     *
     * 该方法通过将单引号替换为双引号来简单处理JavaScript对象格式，
     * 然后验证并返回标准的JSON字符串
     *
     * @param jsObject JavaScript对象格式的字符串（使用单引号）
     * @return 标准JSON格式字符串
     * @throws IllegalArgumentException 当输入的字符串无法解析为有效JSON时抛出
     */
    fun convertSingleJsObjectToStandardJson(jsObject: String): String {
        var result = jsObject

        // 给未加引号的键名添加双引号
        result = result.replace(Regex("""([,{]\s*)([a-zA-Z_$][a-zA-Z0-9_$]*)(\s*:)""")) {
            "${it.groupValues[1]}\"${it.groupValues[2]}\"${it.groupValues[3]}"
        }

        // 将单引号字符串替换为双引号字符串，同时转义内部的双引号
        result = result.replace(Regex("""'([^']*)'""")) {
            "\"${it.groupValues[1].replace("\"", "\\\"")}\""
        }

        return result
    }


    /**
     * 将内容解析为JsonNode对象
     *
     * @param content 需要解析的内容，可以是JSON字符串或其他对象
     * @return 解析后的JsonNode对象
     */
    fun readTree(content: Any): JsonNode {
        return objectMapper.readTree(toJsonString(content))
    }

    /**
     * 从文件中读取内容并解析为JsonNode对象
     *
     * @param content 包含JSON内容的文件
     * @return 解析后的JsonNode对象
     */
    fun readTree(content: File): JsonNode {
        return objectMapper.readTree(content)
    }

    /**
     * 将JSON字符串转换为指定类型的对象
     *
     * @param json JSON字符串
     * @param clazz 目标对象的Class类型
     * @param T 目标对象的泛型类型
     * @return 转换后的对象实例
     */
    fun <T> readValue(json: String, clazz: Class<T>): T {
        return objectMapper.readValue(json, clazz)
    }

    /**
     * 从文件中读取JSON内容并转换为指定类型的对象
     *
     * @param file 包含JSON内容的文件
     * @param clazz 目标对象的Class类型
     * @param T 目标对象的泛型类型
     * @return 转换后的对象实例
     */
    fun <T> readValue(file: File, clazz: Class<T>): T {
        return objectMapper.readValue(file, clazz)
    }

    /**
     * 使用Kotlin的reified泛型将JSON字符串转换为指定类型的对象
     *
     * @param content JSON字符串内容
     * @param T 目标对象的类型，由调用处自动推断
     * @return 转换后的对象实例
     */
    inline fun <reified T> readValue(content: String): T {
        return objectMapper.readValue(content, T::class.java)
    }


    /**
     * 将内容转换为指定JavaType类型的对象，并可选择从特定路径提取数据
     *
     * @param content 需要转换的内容
     * @param valueType 目标JavaType类型
     * @param jsonPath 用于提取特定路径数据的JsonPath对象
     * @param T 目标对象的泛型类型
     * @return 转换后的对象实例
     */
    fun <T> readValue(content: Any, valueType: JavaType, jsonPath: JsonPath): T {
        return readValue(content, valueType, JsonOperateFunction.fromPath(jsonPath))
    }

    /**
     * 将内容转换为指定JavaType类型的对象，并可选择通过操作函数处理
     *
     * @param content 需要转换的内容
     * @param valueType 目标JavaType类型
     * @param function 可选的JsonNode操作函数，用于预处理数据
     * @param T 目标对象的泛型类型
     * @return 转换后的对象实例
     */
    fun <T> readValue(content: Any, valueType: JavaType, function: JsonOperateFunction? = null): T {
        val json = function?.let { toJsonString(it.apply(readTree(content))) }
            ?: toJsonString(content)
        return objectMapper.readValue(json, valueType)
    }

    /**
     * 将内容转换为指定类型的对象列表，并可选择通过操作函数处理
     *
     * @param content 需要转换的内容
     * @param valueType 列表元素的目标类型
     * @param function 可选的JsonNode操作函数，用于预处理数据
     * @param T 列表元素的泛型类型
     * @return 转换后的对象列表
     */
    fun <T> readListValue(content: Any, valueType: Class<T>, function: JsonOperateFunction? = null): List<T> {
        return readValue(content, getListOf(valueType), function)
    }

    /**
     * 将内容转换为指定类型的对象列表，并可选择从特定路径提取数据
     *
     * @param content 需要转换的内容
     * @param valueType 列表元素的目标类型
     * @param path 用于提取特定路径数据的JsonPath对象
     * @param T 列表元素的泛型类型
     * @return 转换后的对象列表
     */
    fun <T> readListValue(content: Any, valueType: Class<T>, path: JsonPath): List<T> {
        return readValue(content, getListOf(valueType), path)
    }

    /**
     * 使用Kotlin的reified泛型将JSON字符串转换为指定类型的对象列表
     *
     * @param content JSON字符串内容
     * @param T 列表元素的类型，由调用处自动推断
     * @return 转换后的对象列表
     */
    inline fun <reified T> readListValue(content: String): List<T> {
        val javaType = objectMapper.typeFactory.constructCollectionType(List::class.java, T::class.java)
        return objectMapper.readValue(content, javaType)
    }

    /**
     * 构造指定元素类型的集合类型
     *
     * @param elementClasses 集合元素的类型
     * @return 构造的CollectionType对象
     */
    private fun getListOf(elementClasses: Class<*>?): CollectionType {
        return objectMapper.typeFactory.constructCollectionType(MutableList::class.java, elementClasses)
    }

    /**
     * 从指定路径读取JSON文件并解析为JsonNode对象
     *
     * @param jsonPath JSON文件的路径
     * @return 解析后的JsonNode对象
     */
    fun getJsonNode(jsonPath: String): JsonNode {
        val file = File(jsonPath)
        return objectMapper.readTree(file) // 使用全局 objectMapper
    }

    /**
     * 将JsonNode对象转换为Map类型
     *
     * @param jsonNode 需要转换的JsonNode对象
     * @param T Map键的类型
     * @param Y Map值的类型
     * @return 转换后的Map对象
     */
    fun <T, Y> jsonNodeToMap(jsonNode: JsonNode): Map<T, Y> {
        @Suppress("UNCHECKED_CAST")
        return objectMapper.convertValue(jsonNode, Map::class.java) as Map<T, Y> // 使用全局 objectMapper
    }

}
