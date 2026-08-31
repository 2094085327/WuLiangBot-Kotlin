package bot.wuliang.riven

/** 紫卡属性在查询和展示层使用的只读模型，与数据库实体解耦。 */
data class RivenAttributeDefinition(
    val id: String,
    val slug: String,
    val zhName: String,
    val enName: String,
    val group: String?,
    val unit: String?,
)

interface RivenAttributeCatalog {
    /** 按中文、英文或 slug 解析用户输入，并显式保留歧义结果。 */
    fun resolve(name: String): RivenAttributeMatch

    /** 为无法识别的输入返回少量相近词条，不返回明显无关的结果。 */
    fun suggest(name: String, limit: Int = 3): List<RivenAttributeDefinition> = emptyList()

    /** 一次加载多个属性，供订单解码批量补齐名称和单位，避免 N+1 查询。 */
    fun findBySlugs(slugs: Collection<String>): Map<String, RivenAttributeDefinition>
}

sealed interface RivenAttributeMatch {
    data class Found(val definition: RivenAttributeDefinition) : RivenAttributeMatch
    data class Ambiguous(val candidates: List<RivenAttributeDefinition>) : RivenAttributeMatch
    data object Unknown : RivenAttributeMatch
}