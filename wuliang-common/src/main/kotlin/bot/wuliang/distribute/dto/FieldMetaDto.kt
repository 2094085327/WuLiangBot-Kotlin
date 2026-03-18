package bot.wuliang.distribute.dto

data class FieldMetaDto(
    val field: String,              // 字段名
    val type: String,               // 类型名
    val isCollection: Boolean = false, // 是否是集合 (List/Set/Array)
    val elementType: String? = null,   // 如果是集合，元素的类型名
    val children: List<FieldMetaDto>? = null, // 如果是对象，子字段列表
)
