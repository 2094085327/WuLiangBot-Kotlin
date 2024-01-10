package bot.demo.txbot.common.utils

import cn.hutool.extra.spring.SpringUtil
import com.fasterxml.jackson.databind.JavaType
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.type.CollectionType
import java.io.File
import java.util.function.Function

/**
 * @Author: Chowhound
 * @Date: 2023/4/13 - 19:30
 * @Description: Jackson工具类, 用于序列化和反序列化
 */
@Suppress("unused", "MemberVisibilityCanBePrivate")
object JacksonUtil {

    // 初始化objectMapper,使用SNAKE_CASE命名法
    val objectMapper: ObjectMapper =
        try {
            SpringUtil.getBean(ObjectMapper::class.java) // 优先使用Spring容器中的ObjectMapper
        } catch (e: Exception) {
            ObjectMapper().apply {
                this.propertyNamingStrategy = PropertyNamingStrategies.SNAKE_CASE
            }
        }

    /**
     * 将对象转换为Json字符串
     */
    fun toJsonString(obj: Any): String {
        if (obj is String) {
            return obj
        }
        return objectMapper.writeValueAsString(obj)
    }

    /**
     * 将Json字符串转换为对象
     */
    fun readTree(content: Any): JsonNode {
        return objectMapper.readTree(toJsonString(content))
    }

    /**
     * 将Json字符串转换为clazz对象
     */
    fun <T> readValue(json: String, clazz: Class<T>): T {
        return objectMapper.readValue(json, clazz)
    }

    /**
     * 将Json字符串转换为valueType对象，可指定路径
     */

    fun <T> readValue(content: Any, valueType: JavaType, jsonPath: JsonPath): T {
        return readValue(content, valueType, JsonOperateFunction.fromPath(jsonPath))
    }

    /**
     * 将Json字符串转换为valueType对象，可指定操作函数
     */
    fun <T> readValue(content: Any, valueType: JavaType, function: JsonOperateFunction? = null): T {
        val json = function?.let { toJsonString(it.apply(readTree(content))) }
            ?: toJsonString(content)
        return objectMapper.readValue(json, valueType)
    }

    /**
     * 将Json字符串转换为valueType对象的List集合，可指定操作函数
     */
    fun <T> readListValue(content: Any, valueType: Class<T>, function: JsonOperateFunction? = null): List<T> {
        return readValue(content, getListOf(valueType), function)
    }

    /**
     * 将Json字符串转换为valueType对象的List集合，可指定路径
     */
    fun <T> readListValue(content: Any, valueType: Class<T>, path: JsonPath): List<T> {
        return readValue(content, getListOf(valueType), path)
    }


    private fun getListOf(elementClasses: Class<*>?): CollectionType {
        return objectMapper.typeFactory.constructCollectionType(MutableList::class.java, elementClasses)
    }

    fun getJsonNode(jsonPath: String): JsonNode {
        val file = File(jsonPath)
        val objectMapper = ObjectMapper()
        return objectMapper.readTree(file)
    }
}


/**
 * 方便按照路径获取JsonNode
 * 如获取{"a":{"b":{"c":1}}}中的1, 可以使用JsonPath.of("a", "b", "c")
 * @param c 路径
 */
@Suppress("unused")
class JsonPath private constructor(c: Collection<String>) :
    ArrayList<String>(c) {
    companion object {
        fun of(vararg paths: String): JsonPath {
//            JavaType(java.util.ArrayList::class.java, String::class.java)
            return JsonPath(listOf(*paths))
        }
    }
}

/**
 * JsonNode操作函数， 可以用于获取JsonNode中的某个节点
 */
@Suppress("unused")
fun interface JsonOperateFunction : Function<JsonNode, JsonNode> {
    companion object {
        fun fromPath(path: JsonPath): JsonOperateFunction {
            return JsonOperateFunction { node: JsonNode ->
                var nodeVar = node
                for (s in path) {
                    nodeVar = nodeVar[s]
                }
                nodeVar
            }
        }

        fun emptyOperate(): JsonOperateFunction {
            return JsonOperateFunction { node: JsonNode -> node }
        }
    }
}
