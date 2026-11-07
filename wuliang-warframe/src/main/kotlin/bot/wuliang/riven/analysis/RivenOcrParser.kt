package bot.wuliang.riven.analysis

import bot.wuliang.aipOcr.OcrLine
import bot.wuliang.riven.RivenAttributeAliases
import org.springframework.stereotype.Component
import java.text.Normalizer
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/** 合并 OCR 断行，并结合词库和整张卡的数值范围恢复识别结果。 */
@Component
class RivenOcrParser(
    private val weapons: RivenWeaponCatalog,
    private val values: RivenValueCatalog,
    private val analysis: RivenValueAnalysis,
) {
    private data class Row(val text: String, val sources: List<OcrLine>) {
        val top get() = sources.minOf { it.top }
        val height get() = max(1, sources.maxOf { it.top + it.height } - top)
        val center get() = top + height / 2.0
    }

    private data class CardNumber(
        val text: String,
        val start: Int,
        val end: Int,
        val multiplier: Boolean,
        val signed: Boolean
    )

    private data class Context(val text: String, val keys: Set<String>, val rows: Set<Int>, val penalty: Double)
    private data class NameMatch(val definition: RivenValueDefinition, val score: Double, val corrected: Boolean)
    private data class NumericValue(val text: String, val corrected: Boolean, val penalty: Double)
    private data class Option(
        val attribute: ParsedRivenAttribute, val score: Double, val keys: Set<String>, val rows: Set<Int>,
        val corrected: Boolean, val missingSign: Boolean,
    )

    private data class Entry(val text: String, val options: List<Option>)
    private data class Path(
        val attributes: List<ParsedRivenAttribute> = emptyList(), val score: Double = 0.0,
        val keys: Set<String> = emptySet(), val rows: Set<Int> = emptySet(), val corrected: Boolean = false,
    )

    /**
     * 解析武器和全部词条，优先自动恢复格式或文字误读，再用整卡数值范围消歧。
     *
     * @param lines OCR 原始文字块及坐标、置信度。
     * @param requestedRank 用户手动指定的等级；未指定时由词条数值反推。
     * @return 已确认词条身份的紫卡；无法校准的数值标记为待核对。
     */
    fun parse(lines: List<OcrLine>, requestedRank: Int? = null): ParsedRiven {
        val rows = mergeRows(lines)
        val header = titleRows(rows.map { it.text })
        val weapon = weapons.find(header)
        val titleIndices = header.indices.filter { weapons.matchesTitle(header[it], weapon) }
        val titleIndex = titleIndices.lastOrNull() ?: 0
        val type = weapon.rivenType?.lowercase()
            ?: throw RivenAnalysisException("当前武器目录缺少紫卡类别，请联系管理员更新词库。")
        val definitions = values.forType(type)
        val aliases = definitions.associateWith { it.aliases.map(::normalize).filter(String::isNotBlank).distinct() }
        val tokens = rows.map { readNumbers(it.text) }
        val entries = mutableListOf<Entry>()
        val unread = mutableListOf<String>()
        for (index in titleIndex + 1 until rows.size) {
            val row = rows[index]
            val numbers = tokens[index]
            numbers.forEachIndexed { numberIndex, number ->
                val contexts = contexts(rows, tokens, index, numberIndex, titleIndex)
                val hint = unitHint(row.text, number)
                val possibleAliases = if (hint == null) aliases else aliases.filterKeys { it.unit == hint }
                val matched = contexts.flatMap { context ->
                    matchNames(context.text, possibleAliases).flatMap { match ->
                        options(number, match.definition).map { (attribute, numeric) ->
                            Option(
                                attribute,
                                match.score - context.penalty - numeric.penalty,
                                context.keys,
                                context.rows,
                                numeric.corrected || match.corrected || context.rows.size > 1,
                                !number.signed && !number.multiplier
                            )
                        }
                    }
                }.sortedByDescending { it.score }
                val best = matched.firstOrNull()?.score
                val choices = if (best == null) emptyList() else matched.filter { it.score >= best - .045 }
                    .distinctBy { Triple(it.attribute.definition.tag, it.attribute.value, it.attribute.positive) }
                    .take(6)
                if (choices.isNotEmpty()) entries += Entry(row.text, choices)
                else if (strongNumber(row.text, number)) unread += row.text
            }
        }
        if (unread.isNotEmpty()) throw RivenAnalysisException(
            "词条看不清呢，请重新使用指令，或改用「紫卡分析 -文字」输入武器名和全部词条数值。"
        )
        if (entries.size !in 2..4) throw RivenAnalysisException("请在图片中保留武器名称和一张紫卡的全部词条。")

        var paths = listOf(Path())
        entries.forEachIndexed { index, entry ->
            paths = paths.flatMap { path ->
                entry.options.mapNotNull { option ->
                    val attribute = option.attribute
                    if (option.missingSign && index < entries.lastIndex && !attribute.positive) return@mapNotNull null
                    if (path.attributes.any { it.definition.tag == attribute.definition.tag } ||
                        path.keys.any { it in option.keys }) return@mapNotNull null
                    val attributes = path.attributes + attribute
                    if (attributes.count { it.positive } > 3 || attributes.count { !it.positive } > 1 ||
                        attributes.count { RivenFusionAnalysis.isFused(it.definition.tag) } > 1) return@mapNotNull null
                    Path(
                        attributes, path.score + option.score, path.keys + option.keys,
                        path.rows + option.rows, path.corrected || option.corrected || option.missingSign
                    )
                }
            }.sortedByDescending { it.score }.take(MAX_PATHS)
        }
        paths = paths.filter { it.attributes.count { attribute -> attribute.positive } in 2..3 }
        if (paths.isEmpty()) throw RivenAnalysisException("词条看不清楚，请重新一张紫卡的完整词条。")
        fun parsed(path: Path, review: Boolean = false) = ParsedRiven(
            weapon.name, weapon.urlName, type, requestedRank, path.attributes, emptyList(), valuesNeedReview = review
        )

        val valid = paths.filter { path ->
            try {
                analysis.analyze(parsed(path)); true
            } catch (_: RivenAnalysisException) {
                false
            }
        }
        val candidates = valid.ifEmpty { paths }
        val best = candidates.first()
        val near = candidates.filter { it.score >= best.score - .025 }
        fun identity(path: Path) = path.attributes.map { it.definition.tag to it.positive }
        if (near.any { identity(it) != identity(best) }) throw RivenAnalysisException(
            "有一条词条的名称或正负号仍无法确认，请保留词条左侧符号重新截图，或改用「紫卡分析 -文字」输入武器名和全部词条数值。"
        )
        val review = valid.isEmpty() || near.any { path ->
            path.attributes.map { it.value } != best.attributes.map { it.value }
        }
        val usedRows = best.rows + titleIndices
        val lowConfidence = usedRows.any { row -> rows[row].sources.any { (it.confidence ?: 1.0) < .85 } }
        val warnings = mutableListOf<String>()
        if (best.corrected || lowConfidence) warnings += "已按词库整理识别结果，请核对武器名和卡面数值。"
        if (best.attributes.any { it.cardDisplayValue?.startsWith('×') == true }) {
            warnings += "阵营伤害倍率按（倍率 − 1）× 100% 换算；倍率卡面取整会影响反推偏差的精度。"
        }
        warnings += "卡面数值存在取整，偏差与浮动位置为近似值。"
        return parsed(best, review).copy(
            warnings = warnings,
            suffix = weapons.findSuffix(header, weapon, best.attributes.count { it.positive })
        )
    }

    /** 以实际数值行划分标题，名称中的 AX-52 等连字符数字不会结束标题。 */
    private fun titleRows(rows: List<String>): List<String> = rows.take(12).takeWhile { row ->
        readNumbers(row).none { strongNumber(row, it) }
    }

    /** 按数值间的文字片段配对；邻行只在足够接近且没有另一组数值时参与恢复。 */
    private fun contexts(
        rows: List<Row>,
        tokens: List<List<CardNumber>>,
        row: Int,
        token: Int,
        title: Int
    ): List<Context> {
        val numbers = tokens[row]
        val current = numbers[token]
        val before = rows[row].text.substring(if (token == 0) 0 else numbers[token - 1].end, current.start)
        val after = rows[row].text.substring(current.end, numbers.getOrNull(token + 1)?.start ?: rows[row].text.length)
        val own = listOf(
            Context(after, setOf("$row:${token + 1}"), setOf(row), 0.0),
            Context(before, setOf("$row:$token"), setOf(row), .008),
            Context("$before $after", setOf("$row:$token", "$row:${token + 1}"), setOf(row), .004),
        ).filter { normalize(nameText(it.text)).isNotBlank() }
        val result = own.toMutableList()
        for (next in listOf(row - 1, row + 1)) {
            if (next <= title || next !in rows.indices || tokens[next].isNotEmpty()) continue
            val a = rows[row]
            val b = rows[next]
            val positioned = a.sources.any { it.height > 0 } && b.sources.any { it.height > 0 }
            if (positioned && abs(a.center - b.center) > max(a.height, b.height) * 1.35) continue
            val distance = if (positioned) abs(a.center - b.center) / max(a.height, b.height) else 1.0
            val extra = Context(b.text, setOf("row:$next"), setOf(row, next), .012 + distance * .018)
            result += extra
            own.forEach { context ->
                result += Context(
                    if (next < row) b.text + " " + context.text else context.text + " " + b.text,
                    context.keys + extra.keys, context.rows + extra.rows, extra.penalty
                )
            }
        }
        return result
    }

    /** 优先完整名称和已知别名；仅对较长名称接受少量漏字或错字。 */
    private fun matchNames(text: String, aliases: Map<RivenValueDefinition, List<String>>): List<NameMatch> {
        val label = nameText(text)
        val normalized = normalize(label)
        if (normalized.isEmpty()) return emptyList()
        val common = RivenAttributeAliases.namesFor(label).map(::normalize).toSet()
        return aliases.mapNotNull { (definition, names) ->
            val score = names.maxOfOrNull { alias ->
                when {
                    normalized == alias -> if (alias.length <= 2) .95 else 1.0
                    alias in common -> .975
                    alias.length >= 3 && normalized.contains(alias) && alias.length.toDouble() / normalized.length >= .72 ->
                        .85 + .1 * alias.length / normalized.length

                    (min(alias.length, normalized.length) >= 3 || aliases.size == 1 && min(
                        alias.length,
                        normalized.length
                    ) >= 2) &&
                            abs(alias.length - normalized.length) <= 2 -> {
                        val distance = editDistance(normalized, alias)
                        val limit = if (min(alias.length, normalized.length) >= 7) 2 else 1
                        if (distance <= limit) .78 + .15 * (1.0 - distance.toDouble() / max(
                            alias.length,
                            normalized.length
                        )) else 0.0
                    }

                    else -> 0.0
                }
            } ?: 0.0
            if (score > 0) NameMatch(definition, score, normalized !in names && names.none { it in common }) else null
        }
    }

    /** 保留完整数值片段，尝试常见字符和小数分隔符恢复；缺失符号交由整卡约束消歧。 */
    private fun options(
        number: CardNumber,
        definition: RivenValueDefinition
    ): List<Pair<ParsedRivenAttribute, NumericValue>> {
        if (number.multiplier && !definition.isFactionDamage) return emptyList()
        return numericValues(number).flatMap { numeric ->
            val raw = numeric.text.toDoubleOrNull()?.takeIf(Double::isFinite) ?: return@flatMap emptyList()
            val signed = if (number.multiplier || number.signed) listOf(raw) else listOf(abs(raw), -abs(raw)).distinct()
            signed.mapNotNull { card ->
                if (number.multiplier && card < 0) return@mapNotNull null
                val value = if (number.multiplier) (card - 1) * 100 else card
                val direction = if (value < 0 || value == 0.0 && numeric.text.startsWith('-')) -1.0 else 1.0
                val positive = direction == definition.positiveSign
                if (positive && !definition.canBePositive || !positive && !definition.canBeNegative) return@mapNotNull null
                val displayed = if (number.multiplier) "×${numeric.text}" else null
                val tolerance = if (number.multiplier) .5 * 10.0.pow(
                    -numeric.text.substringAfter(
                        '.',
                        ""
                    ).length
                ) * 100 + 1e-9 else null
                ParsedRivenAttribute(definition, value, positive, displayed, tolerance) to numeric
            }
        }
    }

    /** 多余小数点保留全部数字形成候选，由整卡范围决定是否能自动确认。 */
    private fun numericValues(number: CardNumber): List<NumericValue> {
        val compact = number.text.filterNot(Char::isWhitespace)
        val normalized = compact.replace('O', '0').replace('o', '0').replace('I', '1')
            .replace('i', '1').replace('l', '1').replace('L', '1').replace(',', '.').replace('。', '.').replace('·', '.')
        val sign = normalized.takeWhile { it == '+' || it == '-' }
        val parts = normalized.removePrefix(sign).split('.')
        if (parts.size <= 2) return listOf(
            NumericValue(
                normalized,
                normalized != compact || compact != number.text,
                0.0
            )
        )
        return listOf(
            sign + parts.first() + "." + parts.drop(1).joinToString(""),
            sign + parts.dropLast(1).joinToString("") + "." + parts.last()
        ).distinct()
            .map { NumericValue(it, true, .04) }
    }

    /** 保留参与识别的原始文字块，避免无关的容量或洗练次数影响词条置信度。 */
    private fun mergeRows(lines: List<OcrLine>): List<Row> {
        if (lines.none { it.height > 0 }) return lines.map { Row(clean(it.text), listOf(it)) }
        val groups = mutableListOf<MutableList<OcrLine>>()
        lines.sortedWith(compareBy<OcrLine> { it.top }.thenBy { it.left }).forEach { line ->
            val row = groups.lastOrNull()
            val anchor = row?.firstOrNull()
            val same = anchor != null && line.height > 0 && anchor.height > 0 &&
                    abs(line.top + line.height / 2.0 - anchor.top - anchor.height / 2.0) <= min(
                line.height,
                anchor.height
            ) * .45
            if (same) row.add(line) else groups += mutableListOf(line)
        }
        return groups.map { row -> Row(clean(row.sortedBy { it.left }.joinToString(" ") { it.text }), row) }
    }

    /** 使用有界短词编辑距离处理 OCR 漏字、插字和错字。 */
    private fun editDistance(a: String, b: String): Int {
        var previous = IntArray(b.length + 1) { it }
        for (i in a.indices) {
            val current = IntArray(b.length + 1)
            current[0] = i + 1
            for (j in b.indices) current[j + 1] = minOf(
                current[j] + 1, previous[j + 1] + 1,
                previous[j] + if (a[i] == b[j]) 0 else 1
            )
            previous = current
        }
        return previous.last()
    }

    companion object {
        private const val MAX_PATHS = 64
        private const val DIGITS = "[0-9OoIiLl]+(?:\\s+(?=[0-9])[0-9OoIiLl]+)*"
        private const val NUMBER = "$DIGITS(?:\\s*[.,。·]\\s*$DIGITS)*"
        private val SIGNED_NUMBER = Regex("(?<![A-Za-z])[+-]\\s*$NUMBER")
        // O/I/l 可作为误读数字，但不能从 satitox 等英文卡名中截取倍率。
        private val MULTIPLIER_PREFIX = Regex("(?i)(?<![A-Za-z0-9])[x×✕]\\s*($NUMBER)(?![A-Za-z0-9])")
        private val MULTIPLIER_SUFFIX = Regex("(?i)(?<![A-Za-z0-9])($NUMBER)\\s*[x×✕](?!\\s*[0-9A-Za-z.,])")
        private val UNSIGNED_NUMBER = Regex("(?<![A-Za-z0-9])$NUMBER")
        private val UNIT = Regex("^\\s*(?:%|米|秒|[ms](?=\\s|\\p{IsHan}|$))", RegexOption.IGNORE_CASE)
        private val ANNOTATION = Regex("\\([^()]*\\)")

        /** 同时读取普通数值和阵营倍率，忽略已经被完整数字覆盖的重复匹配。 */
        private fun readNumbers(text: String): List<CardNumber> {
            val result = mutableListOf<CardNumber>()
            MULTIPLIER_PREFIX.findAll(text)
                .forEach {
                    result += CardNumber(
                        it.groupValues[1], it.range.first, it.range.last + 1,
                        multiplier = true,
                        signed = false
                    )
                }
            MULTIPLIER_SUFFIX.findAll(text).forEach { match ->
                if (result.none { it.start <= match.range.last && it.end > match.range.first }) {
                    result += CardNumber(
                        match.groupValues[1], match.range.first, match.range.last + 1,
                        multiplier = true,
                        signed = false
                    )
                }
            }
            SIGNED_NUMBER.findAll(text).forEach { match ->
                if (result.none { it.start <= match.range.last && it.end > match.range.first }) {
                    result += CardNumber(
                        match.value, match.range.first, match.range.last + 1,
                        multiplier = false,
                        signed = true
                    )
                }
            }
            UNSIGNED_NUMBER.findAll(text).forEach { match ->
                if (result.none { it.start <= match.range.last && it.end > match.range.first } &&
                    (match.value.any(Char::isDigit) || UNIT.containsMatchIn(text.substring(match.range.last + 1)))) {
                    result += CardNumber(
                        match.value, match.range.first, match.range.last + 1,
                        multiplier = false,
                        signed = false
                    )
                }
            }
            val sorted = result.sortedBy { it.start }
            return sorted.filterIndexed { index, number ->
                val next = sorted.getOrNull(index + 1)
                // 锁定图标可能被识别为紧邻真实数值的6等数字；没有词条文字时不另建属性。
                !(!number.signed && !number.multiplier && next != null && (next.signed || next.multiplier) &&
                        text.substring(number.end, next.start).none(Char::isLetterOrDigit))
            }
        }

        private fun strongNumber(text: String, number: CardNumber): Boolean = number.signed || number.multiplier ||
                UNIT.containsMatchIn(text.substring(number.end))

        private fun unitHint(text: String, number: CardNumber): RivenValueUnit? =
            when (UNIT.find(text.substring(number.end))?.value?.trim()?.lowercase()) {
                "%" -> RivenValueUnit.PERCENT
                "米", "m" -> RivenValueUnit.METRES
                "秒", "s" -> RivenValueUnit.SECONDS
                else -> null
            }

        private fun nameText(text: String): String = clean(text).replace(UNIT, "").trim()
        private fun clean(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFKC)
            .replace('−', '-').replace('–', '-').replace('—', '-').replace('﹣', '-')
            .replace(Regex("(?<![A-Za-z])一(?=\\s*\\d)"), "-")
            .replace(ANNOTATION, "").trim()

        private fun normalize(text: String): String = clean(text).lowercase().filter(Char::isLetterOrDigit)
    }
}