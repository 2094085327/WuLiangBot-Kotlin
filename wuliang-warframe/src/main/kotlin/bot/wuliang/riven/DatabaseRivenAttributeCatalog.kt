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

    /** 按完整名称、常用别名、连续包含、中文跳字匹配的优先级解析，只在同一层级保留歧义。 */
    override fun resolve(name: String): RivenAttributeMatch {
        val normalizedName = name.trim()
        if (normalizedName.isEmpty()) return RivenAttributeMatch.Unknown
        val catalog = currentSnapshot()
        val exact = catalog.definitions.filter {
            it.zhName == normalizedName || it.enName.equals(normalizedName, ignoreCase = true) ||
                    it.slug.equals(normalizedName, ignoreCase = true)
        }
        if (exact.isNotEmpty()) return exact.toMatch()

        // 别名直接绑定协议标识，不再对其目标做模糊匹配，避免命中其他属性。
        ATTRIBUTE_ALIASES[normalizedName]?.let { slug ->
            return catalog.definitionsBySlug[slug]?.let(RivenAttributeMatch::Found)
                ?: RivenAttributeMatch.Unknown
        }

        val contained = catalog.definitions.filter {
            it.zhName.contains(normalizedName) || it.enName.contains(normalizedName, ignoreCase = true)
        }
        if (contained.isNotEmpty()) return contained.toMatch()

        return catalog.definitions.filter { it.zhName.containsCharactersInOrder(normalizedName) }.toMatch()
    }

    /** 同一优先级中只有一个候选时才自动选取。 */
    private fun List<RivenAttributeDefinition>.toMatch(): RivenAttributeMatch = when (size) {
        0 -> RivenAttributeMatch.Unknown
        1 -> RivenAttributeMatch.Found(single())
        else -> RivenAttributeMatch.Ambiguous(this)
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

        /** 常用写法统一映射到 Market slug，不依赖中文译名是否带“几率”“伤害”等后缀。 */
        private val ATTRIBUTE_ALIASES = buildMap {
            fun aliases(slug: String, vararg names: String) {
                names.forEach { put(it, slug) }
            }

            aliases("critical_chance", "暴击", "爆击", "暴击率", "爆击率", "暴击几率", "爆击几率")
            aliases("critical_damage", "暴伤", "爆伤", "暴击伤害", "爆击伤害")
            aliases("base_damage_/_melee_damage", "伤害", "基础伤害", "基伤", "近战伤害")
            aliases("fire_rate_/_attack_speed", "射速", "攻速", "攻击速度", "射速/攻击速度")
            aliases("multishot", "多重", "多重射击")
            aliases("toxin_damage", "毒伤", "毒素", "毒素伤害")
            aliases("cold_damage", "冰伤", "冰冻", "冰冻伤害")
            aliases("heat_damage", "火伤", "火焰", "火焰伤害")
            aliases("electric_damage", "电伤", "电击", "电击伤害")
            aliases("impact_damage", "冲击", "冲击伤害")
            aliases("puncture_damage", "穿刺", "穿刺伤害")
            aliases("slash_damage", "切割", "切割伤害")
            aliases("status_chance", "触发", "触发率", "触发几率", "异常几率", "异常状态几率")
            aliases("status_duration", "触发时间", "触发持续时间", "异常持续时间", "异常状态持续时间")
            aliases("projectile_speed", "弹速", "投射物速度", "投射物飞行速度")
            aliases("reload_speed", "装填", "装填速度", "换弹", "换弹速度")
            aliases("magazine_capacity", "弹匣", "弹匣容量")
            aliases("ammo_maximum", "弹药", "弹药上限")
            aliases("punch_through", "穿透")
            aliases("range", "范围", "攻击范围")
            aliases("recoil", "后坐", "后坐力")
            aliases("zoom", "变焦", "变焦倍率")
        }
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