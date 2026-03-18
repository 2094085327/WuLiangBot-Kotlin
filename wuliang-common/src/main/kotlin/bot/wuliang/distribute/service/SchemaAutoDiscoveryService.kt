package bot.wuliang.distribute.service

import bot.wuliang.distribute.annotation.DataSchema
import bot.wuliang.distribute.dto.FieldMetaDto
import org.springframework.beans.factory.getBeansWithAnnotation
import org.springframework.context.ApplicationContext
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import java.lang.reflect.Method
import java.lang.reflect.Modifier
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import kotlin.reflect.KClass
import kotlin.reflect.full.memberProperties
import kotlin.reflect.jvm.javaField
import kotlin.reflect.jvm.javaMethod
import kotlin.reflect.jvm.javaType

@Service
class SchemaAutoDiscoveryService(
    private val applicationContext: ApplicationContext
) {

    /**
     * 缓存结果，避免每次请求都反射
     */
    val schemaCache = mutableMapOf<String, List<FieldMetaDto>>()

    /**
     * 获取指定指令的元数据
     * @param commandKey 指令键
     * @return 字段元数据列表
     */
    fun getSchemaForCommand(commandKey: String): List<FieldMetaDto> {
        return schemaCache.getOrPut(commandKey) {
            discoverSchema(commandKey)
        }
    }

    /**
     * 发现指定指令的元数据
     * @param commandKey 指令键
     * @return 字段元数据列表
     */
    private fun discoverSchema(commandKey: String): List<FieldMetaDto> {
        val controllerBeans = applicationContext.getBeansWithAnnotation<RestController>()

        for ((_, bean) in controllerBeans) {
            // 使用 Kotlin 反射获取 KFunction
            val kClazz = bean::class
            for (kFunction in kClazz.members.filterIsInstance<kotlin.reflect.KFunction<*>>()) {
                val method = kFunction.javaMethod ?: continue
                val annotation = method.getAnnotation(DataSchema::class.java) ?: continue
                if (annotation.commandKey != commandKey && inferCommandKey(method) != commandKey) {
                    continue
                }
                val kReturnType = kFunction.returnType
                val classifier = kReturnType.classifier as? KClass<*>

                // 检查返回类型是否为 RespBean<T>
                if (classifier?.simpleName == "RespBean" && kReturnType.arguments.isNotEmpty()) {
                    val javaType = kReturnType.arguments[0].type?.javaType
                    return extractTargetClassFields(javaType)
                }
            }
        }
        return emptyList()
    }

    /**
     * 递归提取目标类的字段元数据
     * 支持处理：普通类、List<T>、Set<T>、Array<T> 等
     * @param javaType 目标类的 Java Type
     * @return 字段元数据列表
     */
    private fun extractTargetClassFields(javaType: Type?): List<FieldMetaDto> {
        return when (val type = javaType ?: return emptyList()) {
            is Class<*> -> extractFromClass(type)
            is ParameterizedType -> extractFromParameterizedType(type)
            else -> emptyList()
        }
    }


    /**
     * 从普通类提取字段
     * @param clazz 目标类的 Class 对象
     * @return 字段元数据列表
     */
    private fun extractFromClass(clazz: Class<*>): List<FieldMetaDto> {
        if (clazz.isCollectionType()) return emptyList()
        return reflectFieldsUsingKotlin(clazz)
    }

    /**
     * 从泛型类型提取字段
     * @param parameterizedType 泛型类型
     * @return 字段元数据列表
     */
    private fun extractFromParameterizedType(parameterizedType: ParameterizedType): List<FieldMetaDto> {
        val rawType = parameterizedType.rawType as Class<*>

        return if (rawType.isCollectionType()) {
            // 集合类型，递归解析元素类型（如 List<User> 中的 User）
            parameterizedType.actualTypeArguments.firstOrNull()?.let {
                extractTargetClassFields(it)
            } ?: emptyList()
        } else {
            // 其他泛型类（如 Map<K,V> 或自定义泛型类）
            reflectFieldsUsingKotlin(rawType)
        }
    }

    /**
     * 判断是否为集合类型
     * @return 如果是集合类型则返回 true，否则返回 false
     */
    private fun Class<*>.isCollectionType(): Boolean {
        return Collection::class.java.isAssignableFrom(this) ||
                isArray ||
                simpleName in listOf("List", "Set", "Collection")
    }


    /**
     * 利用 Kotlin 反射读取 Data Class 字段（支持嵌套结构）
     * @param clazz 数据类的 Class 对象
     * @return 字段元数据列表（包含完整的层级结构）
     */
    private fun reflectFieldsUsingKotlin(clazz: Class<*>): List<FieldMetaDto> {
        val fields = mutableListOf<FieldMetaDto>()
        val kClass = clazz.kotlin

        for (property in kClass.memberProperties) {
            // 过滤掉 static 字段
            if (Modifier.isStatic(property.javaField?.modifiers ?: 0)) continue

            val returnType = property.returnType
            val fieldMeta = parsePropertyType(returnType, property.name)

            fields.add(fieldMeta)
        }
        return fields
    }

    /**
     * 解析属性类型，构建完整的字段元数据（包括嵌套结构）
     * @param kType Kotlin 类型
     * @return 字段元数据
     */
    private fun parsePropertyType(kType: kotlin.reflect.KType, fieldName: String = ""): FieldMetaDto {
        val classifier = kType.classifier as? KClass<*>
        val className = classifier?.simpleName ?: "Unknown"
        val isNullable = kType.isMarkedNullable

        // 判断是否是集合类型
        val isCollection = classifier?.let {
            Collection::class.java.isAssignableFrom(it.java) ||
                    it.simpleName in listOf("List", "Set", "Collection")
        } ?: false

        return if (isCollection && kType.arguments.isNotEmpty()) {
            // 集合类型：List<T>, Set<T> 等
            val elementType = kType.arguments[0].type
            val elementClassName = (elementType?.classifier as? KClass<*>)?.simpleName ?: "Unknown"

            FieldMetaDto(
                field = fieldName,
                type = className,
                isCollection = true,
                elementType = if (isNullable) "$elementClassName?" else elementClassName,
                children = elementType?.let { parsePropertyChildren(it) }
            )
        } else if (classifier != null && !kType.isPrimitiveOrCommon() && kType.arguments.isEmpty()) {
            // 自定义对象类型（非泛型、非基本类型）
            FieldMetaDto(
                field = fieldName,
                type = if (isNullable) "$className?" else className,
                isCollection = false,
                elementType = null,
                children = try {
                    reflectFieldsUsingKotlin(classifier.java)
                } catch (_: Exception) {
                    null
                }
            )
        } else {
            // 基本类型或简单泛型
            FieldMetaDto(
                field = fieldName,
                type = if (isNullable) "$className?" else className,
                isCollection = false,
                elementType = null,
                children = null
            )
        }
    }

    /**
     * 解析集合或对象的子字段
     * @param kType 元素类型
     * @return 子字段列表
     */
    private fun parsePropertyChildren(kType: kotlin.reflect.KType): List<FieldMetaDto>? {
        val classifier = kType.classifier as? KClass<*> ?: return null

        // 如果是基本类型或不可展开的类型，返回 null
        if (kType.isPrimitiveOrCommon()) {
            return null
        }

        return try {
            val childFields = reflectFieldsUsingKotlin(classifier.java)
            // 为子字段设置正确的字段名
            childFields.map { it.copy(field = it.field) }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 判断是否是基本类型或常见类型，不需要展开子结构
     * @return 是否是基本类型或常见类型
     */
    private fun kotlin.reflect.KType.isPrimitiveOrCommon(): Boolean {
        val classifier = this.classifier as? KClass<*> ?: return true
        val className = classifier.simpleName

        return className in listOf(
            "String", "Int", "Long", "Short", "Byte", "Float", "Double", "Boolean", "Char",
            "UInt", "ULong", "UShort", "UByte",
            "Number", "Any", "Unit"
        )
    }

    /**
     * 从 @GetMapping 路径中提取 commandKey (例如 /incarnon -> incarnon)
     * @param method 目标方法的 Method 对象
     * @return 提取到的 commandKey，或 null 如果未找到
     */
    private fun inferCommandKey(method: Method): String? {
        val getMapping = method.getAnnotation(GetMapping::class.java) ?: return null
        return getMapping.value.firstOrNull()?.trim('/')
    }
}
