package bot.wuliang.riven.range

import bot.wuliang.riven.analysis.RivenAnalysisException
import bot.wuliang.riven.analysis.RivenCombination
import java.text.Normalizer

data class RivenRangeQuery(val target: String, val combination: RivenCombination) {
    companion object {
        const val HELP = "用法：词条范围 武器名 [组合]\n" +
                "例：词条范围 布莱顿\n词条范围 布莱顿 2+1\n词条范围 近战 2+\n" +
                "组合支持 2+、2+1、3+、3+1，省略时使用3+1。负面可带末尾减号。\n" +
                "也可按步枪、霰弹枪、手枪、近战、空战枪械、组合枪、自制近战查询。"
        private val COMBINATION = Regex("(?:^|\\s*)([23])\\s*\\+(?:\\s*(1)\\s*-?)?$")

        fun parse(input: String): RivenRangeQuery {
            if (input.length > 120) throw RivenAnalysisException("查询内容过长，请只输入武器名和词条组合。")
            val text = Normalizer.normalize(input, Normalizer.Form.NFKC)
                .replace(Regex("[−–—﹣]"), "-").trim()
            val match = COMBINATION.find(text)
            val target = if (match == null) text else text.substring(0, match.range.first).trim()
            if (target.isBlank()) throw RivenAnalysisException(HELP)
            if ('+' in target || Regex("(?:^|\\s)[23](?:\\s|$)").containsMatchIn(target)) {
                throw RivenAnalysisException("词条组合不正确，请使用 2+、2+1、3+ 或 3+1。\n例：词条范围 布莱顿 2+1")
            }
            val combination = match?.let {
                RivenCombination.forCounts(it.groupValues[1].toInt(), if (it.groupValues[2].isEmpty()) 0 else 1)
            } ?: RivenCombination.THREE_POSITIVE_ONE_NEGATIVE
            return RivenRangeQuery(target, combination)
        }
    }
}