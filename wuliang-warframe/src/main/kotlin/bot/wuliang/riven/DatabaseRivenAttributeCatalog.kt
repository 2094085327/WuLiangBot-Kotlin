package bot.wuliang.riven

import bot.wuliang.entity.WfRivenAttributeEntity
import bot.wuliang.mapper.WfRivenAttributeMapper
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper
import org.springframework.stereotype.Component

/** 基于独立紫卡属性表实现领域目录查询。 */
@Component
class DatabaseRivenAttributeCatalog(
    private val attributeMapper: WfRivenAttributeMapper,
) : RivenAttributeCatalog {
    override fun resolve(name: String): RivenAttributeMatch {
        val regex = name.toCharArray().joinToString(".*") { it.toString() }
        val candidates = attributeMapper.selectList(
            QueryWrapper<WfRivenAttributeEntity>()
                .apply("zh REGEXP {0}", regex)
                .or()
                .like("en", "%$name%")
                .or()
                .eq("url_name", name)
        ).mapNotNull(::toDefinition)

        // 精确匹配优先；只有没有精确结果时才采用模糊匹配，避免短词误命中。
        val exact = candidates.filter {
            it.zhName == name || it.enName.equals(name, ignoreCase = true) || it.slug.equals(name, ignoreCase = true)
        }
        return when {
            exact.size == 1 -> RivenAttributeMatch.Found(exact.single())
            exact.size > 1 -> RivenAttributeMatch.Ambiguous(exact)
            candidates.size == 1 -> RivenAttributeMatch.Found(candidates.single())
            candidates.isEmpty() -> RivenAttributeMatch.Unknown
            else -> RivenAttributeMatch.Ambiguous(candidates)
        }
    }

    override fun findBySlugs(slugs: Collection<String>): Map<String, RivenAttributeDefinition> {
        if (slugs.isEmpty()) return emptyMap()
        return attributeMapper.selectList(
            QueryWrapper<WfRivenAttributeEntity>().`in`("url_name", slugs.distinct())
        ).mapNotNull(::toDefinition).associateBy { it.slug }
    }

    override fun suggest(name: String, limit: Int): List<RivenAttributeDefinition> {
        if (name.isBlank() || limit <= 0) return emptyList()
        val normalizedName = name.trim().lowercase()
        return attributeMapper.selectList(QueryWrapper<WfRivenAttributeEntity>())
            .mapNotNull(::toDefinition)
            .map { definition ->
                definition to minOf(
                    normalizedDistance(normalizedName, definition.zhName.lowercase()),
                    normalizedDistance(normalizedName, definition.enName.lowercase()),
                    normalizedDistance(normalizedName, definition.slug.lowercase()),
                )
            }
            .filter { (_, distance) -> distance <= MAX_SUGGESTION_DISTANCE }
            .sortedBy { (_, distance) -> distance }
            .take(limit)
            .map { (definition) -> definition }
    }

    private fun normalizedDistance(left: String, right: String): Double {
        if (left.isEmpty() || right.isEmpty()) return 1.0
        if (right.contains(left) || left.contains(right)) return 0.0
        return editDistance(left, right).toDouble() / maxOf(left.length, right.length)
    }

    private fun editDistance(left: String, right: String): Int {
        var previous = IntArray(right.length + 1) { it }
        left.forEachIndexed { leftIndex, leftChar ->
            val current = IntArray(right.length + 1)
            current[0] = leftIndex + 1
            right.forEachIndexed { rightIndex, rightChar ->
                current[rightIndex + 1] = minOf(
                    current[rightIndex] + 1,
                    previous[rightIndex + 1] + 1,
                    previous[rightIndex] + if (leftChar == rightChar) 0 else 1,
                )
            }
            previous = current
        }
        return previous[right.length]
    }

    private fun toDefinition(entity: WfRivenAttributeEntity?): RivenAttributeDefinition? {
        entity ?: return null
        return RivenAttributeDefinition(
            id = entity.id ?: return null,
            slug = entity.urlName ?: return null,
            zhName = entity.zhName.orEmpty(),
            enName = entity.enName.orEmpty(),
            group = entity.rGroup,
            unit = entity.unit,
        )
    }

    companion object {
        private const val MAX_SUGGESTION_DISTANCE = 0.6
    }
}