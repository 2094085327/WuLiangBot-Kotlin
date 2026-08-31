package bot.wuliang.riven

sealed interface RivenNegativeConstraint {
    /** 用户明确要求拍卖条目不包含负属性。 */
    data object None : RivenNegativeConstraint
    data class Attribute(val definition: RivenAttributeDefinition) : RivenNegativeConstraint
}

data class RivenQueryCriteria(
    val weaponSlug: String,
    val positiveAttributes: List<RivenAttributeDefinition>,
    val negativeConstraint: RivenNegativeConstraint?,
) {
    /**
     * 转换为 Warframe Market v1 拍卖接口参数。
     * negativeConstraint 为 null 表示“不筛选负属性”，与明确的“无负”不同。
     */
    fun toMarketParams(): Map<String, String> = buildMap {
        put("weapon_url_name", weaponSlug)
        put("sort_by", "price_asc")
        if (positiveAttributes.isNotEmpty()) {
            put("positive_stats", positiveAttributes.joinToString(",") { it.slug })
        }
        when (val negative = negativeConstraint) {
            RivenNegativeConstraint.None -> put("negative_stats", "none")
            is RivenNegativeConstraint.Attribute -> put("negative_stats", negative.definition.slug)
            null -> Unit
        }
    }
}

sealed interface RivenCriteriaResolution {
    data class Success(val criteria: RivenQueryCriteria) : RivenCriteriaResolution
    data class UnknownAttribute(
        val token: String,
        val suggestions: List<RivenAttributeDefinition>,
    ) : RivenCriteriaResolution
    data class AmbiguousAttribute(
        val token: String,
        val candidates: List<RivenAttributeDefinition>,
    ) : RivenCriteriaResolution
    data class ConflictingNegativeAttributes(val tokens: List<String>) : RivenCriteriaResolution
}