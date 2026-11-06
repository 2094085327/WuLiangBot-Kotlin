package bot.wuliang.riven.analysis

import bot.wuliang.riven.DatabaseRivenAttributeCatalog
import bot.wuliang.riven.RivenGroups
import bot.wuliang.riven.RivenNameAffix
import bot.wuliang.service.WfRivenService
import bot.wuliang.translation.PublicExportService
import org.springframework.stereotype.Component
import java.text.Normalizer

/** 紫卡分析专用的只读武器信息 */
data class RivenAnalysisWeapon(
    val id: String?,
    val name: String,
    val urlName: String,
    val rivenType: String?,
)

/** 首次分析时加载武器及名称索引。 */
@Component
class RivenWeaponCatalog(
    private val weapons: WfRivenService,
    private val translations: PublicExportService,
    private val attributes: DatabaseRivenAttributeCatalog,
) {
    @Volatile
    private var snapshot: CatalogSnapshot? = null

    /** 文字模式优先完整名称，其次匹配简称；无完整匹配时允许去掉变体前缀。 */
    fun findText(name: String): RivenAnalysisWeapon {
        val names = currentSnapshot().names
        val candidates = titleVariants(name).map(::normalizeName).filter(String::isNotBlank).distinct()
        for (exact in listOf(true, false)) {
            for (candidate in candidates) {
                val matches = names.filter {
                    if (exact) it.name == candidate else it.name.contains(candidate)
                }.map { it.weapon }.distinctBy { it.urlName }
                if (matches.size > 1) throw RivenAnalysisException(weaponChoices(matches))
                matches.singleOrNull()?.let { return it }
            }
        }
        throw RivenAnalysisException("未识别到紫卡武器。请检查武器名称。")
    }

    /** 优先完整名称；无匹配时才去掉变体前缀 */
    fun find(titles: List<String>): RivenAnalysisWeapon {
        val names = currentSnapshot().names
        fun matches(candidates: List<String>): List<WeaponName> = candidates.flatMap { title ->
            val normalized = normalizeName(title)
            names.filter { normalized.startsWith(it.name) }
        }

        val matches = matches(titles).ifEmpty {
            matches(titles.map { titleVariants(it).last() })
        }
        val bestLength = matches.maxOfOrNull { it.name.length }
        val best = matches.filter { it.name.length == bestLength }.map { it.weapon }.distinctBy { it.id }
        if (best.size > 1) throw RivenAnalysisException(weaponChoices(best))
        return best.singleOrNull()
            ?: throw RivenAnalysisException("未识别到紫卡武器名称，请发送包含完整武器名称和全部词条的单张紫卡图片。")
    }

    /** 判断 OCR 行是否实际命中当前武器名称，供识别范围和置信度统计使用。 */
    fun matchesTitle(title: String, weapon: RivenAnalysisWeapon): Boolean = titleVariants(title).any { text ->
        val normalized = normalizeName(text)
        currentSnapshot().names.any { it.weapon.urlName == weapon.urlName && normalized.startsWith(it.name) }
    }

    /** 只合并标题内实际识别的文字。词根不完整时隐藏后缀。 */
    fun findSuffix(titles: List<String>, weapon: RivenAnalysisWeapon, positiveCount: Int): String? {
        if (positiveCount !in 2..3) return null
        // 名称只是展示字段；词根暂不可用时，不影响已有数值分析。
        val affixes = try {
            attributes.nameAffixes()
        } catch (_: Exception) {
            return null
        }
        if (affixes.isEmpty()) return null
        val suffixes = affixes.groupBy { it.suffix }
        val aliases = currentSnapshot().names.filter { it.weapon.urlName == weapon.urlName }
        titles.forEachIndexed { index, title ->
            val (text, alias) = titleVariants(title).firstNotNullOfOrNull { text ->
                val normalized = normalizeName(text)
                aliases.filter { normalized.startsWith(it.name) }.maxByOrNull { it.name.length }
                    ?.let { text to it }
            } ?: return@forEachIndexed
            var letters = 0
            val end = text.indexOfFirst { it.isLetterOrDigit() && ++letters == alias.name.length }
            val inline = text.substring(end + 1).trim(' ', '「', '」', '[', ']', '【', '】', ':', '：')
            var candidate = if (inline.isBlank()) "" else suffixFragment(inline) ?: return@forEachIndexed
            if (completeSuffix(candidate, positiveCount, affixes, suffixes)) return candidate
            // 最多读取三条紧邻续行。属性数值、中文正文和其他内容均不拼入卡名。
            for (next in titles.drop(index + 1).take(3)) {
                val fragment = suffixFragment(next) ?: break
                candidate += fragment
                if (candidate.length > 64) break
                if (completeSuffix(candidate, positiveCount, affixes, suffixes)) return candidate
            }
        }
        return null
    }

    private fun titleVariants(title: String): List<String> {
        val text = Normalizer.normalize(title, Normalizer.Form.NFKC).trim()
        return listOf(text, text.replace(Regex("^(?:赤毒|信条|kuva\\s*|tenet\\s*)", RegexOption.IGNORE_CASE), ""))
            .distinct()
    }

    private fun normalizeName(text: String): String =
        Normalizer.normalize(text, Normalizer.Form.NFKC).lowercase().filter(Char::isLetterOrDigit)

    /** 候选只来自当前匹配层，展示完整名称；同名武器附带可直接输入的目录标识。 */
    private fun weaponChoices(matches: List<RivenAnalysisWeapon>): String {
        val nameCounts = matches.groupingBy { it.name }.eachCount()
        val options = matches.sortedWith(compareBy({ it.name }, { it.urlName })).take(10)
            .mapIndexed { index, weapon ->
                val name =
                    if (nameCounts.getValue(weapon.name) > 1) "${weapon.name}（${weapon.urlName}）" else weapon.name
                "${index + 1}. $name"
            }.joinToString("\n")
        val remaining = if (matches.size > 10) "\n另有${matches.size - 10}项，请补充武器名称以缩小范围。" else ""
        return "重新发送完整的文字分析指令，也许你在找的是：\n$options$remaining"
    }

    private fun suffixFragment(value: String): String? {
        val text = Normalizer.normalize(value, Normalizer.Form.NFKC)
            .trim(' ', '「', '」', '[', ']', '【', '】')
            .replace(Regex("[−–—﹣]"), "-").replace(Regex("\\s+"), "")
        return text.takeIf { it.length in 1..64 && Regex("[A-Za-z-]+").matches(it) }
    }

    private fun completeSuffix(
        value: String, positiveCount: Int, affixes: List<RivenNameAffix>,
        suffixes: Map<String, List<RivenNameAffix>>
    ): Boolean {
        val parts = value.lowercase().split('-')
        if (parts.size != positiveCount - 1 || parts.any { it.isEmpty() }) return false
        fun matchesCore(core: String, excluded: String?): Boolean = affixes.any { middle ->
            middle.attribute != excluded && core.startsWith(middle.prefix) &&
                    suffixes[core.removePrefix(middle.prefix)].orEmpty().any { last ->
                        last.attribute != middle.attribute && last.attribute != excluded
                    }
        }
        return if (positiveCount == 2) matchesCore(parts.single(), null)
        else affixes.any { first -> first.prefix == parts.first() && matchesCore(parts.last(), first.attribute) }
    }

    /** 与首次加载使用同一把锁，避免更新后被尚未完成的旧加载覆盖。 */
    @Synchronized
    fun invalidate() {
        snapshot = null
    }

    /** 双重检查保证同一版本并发首次访问只查询一次，正常命中无需加锁。 */
    private fun currentSnapshot(): CatalogSnapshot {
        snapshot?.takeIf { it.revision == translations.revision }?.let { return it }
        return synchronized(this) {
            snapshot?.takeIf { it.revision == translations.revision }
                ?: loadSnapshot().also { snapshot = it }
        }
    }

    private fun loadSnapshot(): CatalogSnapshot {
        // 译名读取依赖当前翻译索引；构建期间切换版本时重试，避免发布混合版本。
        while (true) {
            val revision = translations.revision
            val names = weapons.selectAllRivenData()
                .filter { it.rGroup !in setOf(RivenGroups.LICH, RivenGroups.SISTER) && !it.urlName.isNullOrBlank() }
                .flatMap { entity ->
                    val weapon = RivenAnalysisWeapon(
                        entity.id, entity.zhName ?: entity.enName.orEmpty(),
                        entity.urlName.orEmpty(), entity.rivenType
                    )
                    listOfNotNull(entity.zhName, entity.enName, entity.urlName?.replace('_', ' '))
                        .map(::normalizeName)
                        .filter(String::isNotBlank).distinct().map { WeaponName(it, weapon) }
                }
            if (revision != translations.revision) continue
            if (names.isEmpty()) throw RivenAnalysisException("紫卡武器词库为空，请联系管理员更新词库。")
            return CatalogSnapshot(revision, names)
        }
    }

    private data class WeaponName(val name: String, val weapon: RivenAnalysisWeapon)
    private data class CatalogSnapshot(val revision: String, val names: List<WeaponName>)
}