package bot.wuliang.riven

import bot.wuliang.entity.WfRivenAttributeEntity
import bot.wuliang.mapper.WfRivenAttributeMapper
import org.springframework.stereotype.Component

/** 基于独立紫卡属性表实现领域目录查询。 */
@Component
class DatabaseRivenAttributeCatalog(
    private val attributeMapper: WfRivenAttributeMapper,
) : RivenAttributeCatalog {
    @Volatile
    private var snapshot: CatalogSnapshot? = null

    /** 解析属性名称，优先采用唯一精确匹配，其次采用唯一模糊匹配，并区分未知或多候选结果。 */
    override fun resolve(name: String): RivenAttributeMatch {
        val normalizedName = name.trim()
        val candidates = currentSnapshot().definitions.filter { definition ->
            definition.zhName.containsCharactersInOrder(normalizedName) ||
                    definition.enName.contains(normalizedName, ignoreCase = true) ||
                    definition.slug.equals(normalizedName, ignoreCase = true)
        }

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

    /** 按去重后的 Market 属性标识查询快照；结果只包含已经收录的标识。 */
    override fun findBySlugs(slugs: Collection<String>): Map<String, RivenAttributeDefinition> {
        if (slugs.isEmpty()) return emptyMap()
        val definitionsBySlug = currentSnapshot().definitionsBySlug
        return slugs.distinct().mapNotNull { slug -> definitionsBySlug[slug]?.let { slug to it } }.toMap()
    }

    /** 按中英文名称及 slug 的最小归一化编辑距离推荐候选，最多返回 [limit] 项。 */
    override fun suggest(name: String, limit: Int): List<RivenAttributeDefinition> {
        if (name.isBlank() || limit <= 0) return emptyList()
        val normalizedName = name.trim().lowercase()
        return currentSnapshot().definitions
            .asSequence()
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
            .toList()
    }

    /** 使用刚完成同步的数据原子替换查询快照，避免再次读取数据库。 */
    @Synchronized
    fun refresh(entities: Collection<WfRivenAttributeEntity>) {
        snapshot = CatalogSnapshot.from(entities.mapNotNull(::toDefinition))
    }

    /**
     * 首次查询时从独立属性表构建快照，后续由同步流程主动刷新，避免每次查询访问数据库。
     */
    private fun currentSnapshot(): CatalogSnapshot = snapshot ?: synchronized(this) {
        snapshot ?: CatalogSnapshot.from(
            attributeMapper.selectList(null).mapNotNull(::toDefinition)
        ).also { snapshot = it }
    }

    /** 判断 [query] 的字符是否作为子序列按顺序出现在当前字符串中，空查询不匹配。 */
    private fun String.containsCharactersInOrder(query: String): Boolean {
        if (query.isEmpty()) return false
        var queryIndex = 0
        for (character in this) {
            if (character == query[queryIndex] && ++queryIndex == query.length) return true
        }
        return false
    }

    /** 计算按最长字符串长度归一化的编辑距离；互相包含时视为零距离，空字符串返回一。 */
    private fun normalizedDistance(left: String, right: String): Double {
        if (left.isEmpty() || right.isEmpty()) return 1.0
        if (right.contains(left) || left.contains(right)) return 0.0
        return editDistance(left, right).toDouble() / maxOf(left.length, right.length)
    }

    /** 使用动态规划计算 [left] 与 [right] 的最少插入、删除和替换次数。 */
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

    /**
     * 保留属性表中的 Market 中文名称；缺失时依次回退英文和 slug，不调用 Plus 翻译。
     */
    private fun toDefinition(entity: WfRivenAttributeEntity?): RivenAttributeDefinition? {
        entity ?: return null
        return RivenAttributeDefinition(
            id = entity.id ?: return null,
            slug = entity.urlName ?: return null,
            zhName = entity.zhName?.takeIf { it.isNotBlank() }
                ?: entity.enName?.takeIf { it.isNotBlank() } ?: entity.urlName,
            enName = entity.enName.orEmpty(),
            group = entity.rGroup,
            unit = entity.unit,
        )
    }

    companion object {
        private const val MAX_SUGGESTION_DISTANCE = 0.6
    }

    private data class CatalogSnapshot(
        val definitions: List<RivenAttributeDefinition>,
        val definitionsBySlug: Map<String, RivenAttributeDefinition>,
    ) {
        companion object {
            /** 复制属性定义列表并建立 slug 索引，生成供查询原子发布的目录快照。 */
            fun from(definitions: List<RivenAttributeDefinition>) = CatalogSnapshot(
                definitions = definitions.toList(),
                definitionsBySlug = definitions.associateBy { it.slug },
            )
        }
    }
}