package bot.wuliang.riven.analysis

import bot.wuliang.riven.RivenAttributeAliases
import org.springframework.stereotype.Component
import java.text.Normalizer

/** 文字输入支持武器简称和变体前缀；词条按完整名称及市场查询共用别名匹配。 */
@Component
class RivenTextParser(
    private val weapons: RivenWeaponCatalog,
    private val values: RivenValueCatalog,
) {
    fun parse(input: String): ParsedRiven {
        if (input.length > 2000) throw RivenAnalysisException("文字内容过长，请只输入一张紫卡的武器和词条。")
        val parts = Normalizer.normalize(input, Normalizer.Form.NFKC).split('/').map(String::trim)
        if (parts.size !in 3..5 || parts.any(String::isBlank)) throw RivenAnalysisException(FORMAT_MESSAGE)
        val weapon = weapons.findText(parts.first())
        val type = weapon.rivenType?.lowercase()
            ?: throw RivenAnalysisException("当前武器目录缺少紫卡类别，请联系管理员更新词库。")
        val definitions = values.forType(type)
        val attributes = parts.drop(1).map { part ->
            val match = ATTRIBUTE.matchEntire(part) ?: throw RivenAnalysisException(
                "词条格式不正确：「$part」。请使用「+220.9% 伤害」这样的格式，保留正负号。"
            )
            val numericText = match.groupValues[1].replace(Regex("\\s+"), "")
            val value = numericText.toDoubleOrNull()?.takeIf(Double::isFinite)
                ?: throw RivenAnalysisException("词条数值无法识别：「$numericText」。")
            val name = match.groupValues[3].trim()
            val names = (RivenAttributeAliases.namesFor(name) + name).map(::normalize).toSet()
            val matches = definitions.filter { definition ->
                definition.aliases.any { normalize(it) in names }
            }.distinctBy { it.tag }
            if (matches.isEmpty()) throw RivenAnalysisException("未识别到当前武器可用的词条：「$name」。请检查词条名称。")
            val definition = matches.singleOrNull()
                ?: throw RivenAnalysisException(
                    "请将「$name」替换为以下词条名称之一，再重新发送完整指令：\n" +
                        matches.map { it.name }.distinct().sorted()
                            .mapIndexed { index, option -> "${index + 1}. $option" }
                            .joinToString("\n"))
            val unit = match.groupValues[2].lowercase()
            val allowedUnits = when (definition.unit) {
                RivenValueUnit.PERCENT -> setOf("", "%")
                RivenValueUnit.METRES -> setOf("", "米", "m")
                RivenValueUnit.SECONDS -> setOf("", "秒", "s")
                RivenValueUnit.NUMBER -> setOf("")
            }
            if (unit !in allowedUnits) throw RivenAnalysisException("「${definition.name}」的数值单位不正确。")
            val sign = if (numericText.startsWith('-')) -1.0 else 1.0
            val positive = sign == definition.positiveSign
            if ((positive && !definition.canBePositive) || (!positive && !definition.canBeNegative)) {
                throw RivenAnalysisException("「${definition.name}」不支持这个正负方向，请检查词条。")
            }
            ParsedRivenAttribute(
                definition, value, positive,
                cardDisplayValue = numericText + definition.unit.symbol
            )
        }
        if (attributes.map { it.definition.tag }.distinct().size != attributes.size) {
            throw RivenAnalysisException("词条重复，请只输入一张紫卡上的不同词条。")
        }
        val positives = attributes.count { it.positive }
        if (positives !in 2..3 || attributes.size - positives !in 0..1) {
            throw RivenAnalysisException("词条组合不正确：应为两条或三条正面词条，至多一条负面词条。")
        }
        return ParsedRiven(weapon.name, weapon.urlName, type, null, attributes, emptyList())
    }

    private fun normalize(value: String) = RivenAttributeAliases.normalize(value)

    companion object {
        const val FORMAT_MESSAGE =
            "文字格式：紫卡分析 -文字 武器名/+数值% 词条/+数值% 词条。例：紫卡分析 -文字 绝路/+220.9% 伤害/+108.6% 触发几率"
        private val ATTRIBUTE = Regex(
            "^([+-]\\s*\\d+(?:\\.\\d+)?)\\s*(%|米|秒|m(?=\\s)|s(?=\\s))?\\s*([\\s\\S]+)$",
            RegexOption.IGNORE_CASE
        )
    }
}