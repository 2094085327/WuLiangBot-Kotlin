package bot.wuliang.riven

import org.springframework.stereotype.Component

/**
 * 将命令参数解析为类型化条件：普通词条为正属性，“负词条”或“词条负”为负属性，
 * “无负”要求无负属性，“数字+洗”仅作为洗练次数参数而跳过。
 */
@Component
class RivenQueryCriteriaResolver(
    private val attributeCatalog: RivenAttributeCatalog,
) {
    fun resolve(weaponSlug: String, tokens: List<String>): RivenCriteriaResolution {
        val positiveAttributes = mutableListOf<RivenAttributeDefinition>()
        var negativeConstraint: RivenNegativeConstraint? = null
        val negativeTokens = mutableListOf<String>()

        tokens.asSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .filterNot { REROLL_TOKEN.matches(it) }
            .forEach { token ->
                if (token == NO_NEGATIVE_TOKEN) {
                    negativeTokens += token
                    if (negativeTokens.size > 1) {
                        return RivenCriteriaResolution.ConflictingNegativeAttributes(negativeTokens)
                    }
                    negativeConstraint = RivenNegativeConstraint.None
                    return@forEach
                }

                val isNegative = token.startsWith(NEGATIVE_MARKER) || token.endsWith(NEGATIVE_MARKER)
                val rawAttributeName = if (isNegative) token.trim(NEGATIVE_MARKER.single()) else token
                val attributeName = ATTRIBUTE_ALIASES[rawAttributeName] ?: rawAttributeName
                when (val resolved = attributeCatalog.resolve(attributeName)) {
                    RivenAttributeMatch.Unknown -> return RivenCriteriaResolution.UnknownAttribute(
                        token = token,
                        suggestions = attributeCatalog.suggest(rawAttributeName),
                    )
                    is RivenAttributeMatch.Ambiguous -> return RivenCriteriaResolution.AmbiguousAttribute(
                        token,
                        resolved.candidates,
                    )
                    is RivenAttributeMatch.Found -> {
                        if (isNegative) {
                            negativeTokens += token
                            if (negativeTokens.size > 1) {
                                return RivenCriteriaResolution.ConflictingNegativeAttributes(negativeTokens)
                            }
                            negativeConstraint = RivenNegativeConstraint.Attribute(resolved.definition)
                        } else {
                            positiveAttributes += resolved.definition
                        }
                    }
                }
            }

        return RivenCriteriaResolution.Success(
            RivenQueryCriteria(
                weaponSlug = weaponSlug,
                positiveAttributes = positiveAttributes.distinctBy { it.slug },
                negativeConstraint = negativeConstraint,
            )
        )
    }

    companion object {
        private const val NO_NEGATIVE_TOKEN = "无负"
        private const val NEGATIVE_MARKER = "负"
        private val REROLL_TOKEN = Regex("""\d+洗""")
        private val ATTRIBUTE_ALIASES = mapOf(
            "暴击" to "critical_chance",
            "爆击" to "critical_chance",
            "暴击率" to "critical_chance",
            "爆击率" to "critical_chance",
            "暴伤" to "critical_damage",
            "爆伤" to "critical_damage",
        )
    }
}