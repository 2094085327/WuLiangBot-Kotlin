package bot.wuliang.utils

import java.util.concurrent.TimeUnit

object WfStatus {
    private val timeReplacements = mapOf(
        "d " to "天",
        " d" to "天",
        "h " to "小时",
        " h" to "小时",
        "m " to "分",
        " m" to "分",
        "s " to "秒",
        " s" to "秒",
        "s" to "秒"
    )

    private val factionReplacements = mapOf(
        "Grineer" to "G系",
        "Corpus" to "C系",
        "Infested" to "I系",
        "Infestation" to "I系",
        "Orokin" to "O系",
        "奥罗金" to "O系",
        "Crossfire" to "多方交战",
        "The Murmur" to "M系",
        "Narmer" to "合一众"
    )


    fun String.replaceTime(): String {
        return timeReplacements.entries.fold(this) { acc: String, entry: Map.Entry<String, String> ->
            acc.replace(entry.key, entry.value)
        }
    }

    fun String.replaceFaction(): String {
        return factionReplacements.entries.fold(this) { acc: String, entry: Map.Entry<String, String> ->
            acc.replace(entry.key, entry.value)
        }
    }

    fun String.parseDuration(): Long {
        // 定义正则表达式，匹配天数、小时数、分钟数和秒数
        val regex = Regex("(?:(\\d+)\\s*天)?(?:\\s*(\\d+)\\s*小时)?(?:\\s*(\\d+)\\s*(?:分|分钟))?(?:\\s*(\\d+)\\s*秒)?")

        // 使用正则表达式进行匹配
        val matchResult = regex.find(this) ?: return 0L

        // 提取匹配结果
        val (days, hours, minutes, seconds) = matchResult.destructured

        // 将提取的字符串转换为整数，并处理可能的空字符串情况
        val day = days.toIntOrNull() ?: 0
        val hour = hours.toIntOrNull() ?: 0
        val minute = minutes.toIntOrNull() ?: 0
        val second = seconds.toIntOrNull() ?: 0

        // 计算总秒数
        return day * TimeUnit.DAYS.toSeconds(1) +
                hour * TimeUnit.HOURS.toSeconds(1) +
                minute * TimeUnit.MINUTES.toSeconds(1) +
                second * TimeUnit.SECONDS.toSeconds(1)
    }
}