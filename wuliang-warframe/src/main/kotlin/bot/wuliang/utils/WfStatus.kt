package bot.wuliang.utils

object WfStatus {
    private val factionReplacements = mapOf(
        "Grineer" to "G系",
        "FC_GRINEER" to "G系",
        "Corpus" to "C系",
        "FC_CORPUS" to "C系",
        "Infested" to "I系",
        "FC_INFESTATION" to "I系",
        "Infestation" to "I系",
        "Orokin" to "O系",
        "奥罗金" to "O系",
        "Crossfire" to "多方交战",
        "The Murmur" to "M系",
        "Narmer" to "合一众"
    )
    val WarframeElement = mapOf(
        "Electricity" to "electricity",
        "Corrosive" to "corrosive",
        "Toxin" to "toxin",
        "Heat" to "heat",
        "Blast" to "blast",
        "Radiation" to "radiation",
        "Cold" to "cold",
        "Viral" to "viral",
        "Magnetic" to "magnetic",
        "Gas" to "gas",
        "Void" to "void",
    )

    /**
     * 科研类型映射（深层科研 / 时光科研）
     */
    val conquestTypeMap = mapOf(
        "CT_LAB" to "深层科研",
        "CT_HEX" to "时光科研"
    )


    /**
     * 季节映射
     */
    val calendarSeasonMap = mapOf(
        "CST_SPRING" to "春季",
        "CST_SUMMER" to "夏季",
        "CST_FALL" to "秋季",
        "CST_WINTER" to "冬季"
    )

    /**
     * 事件类型映射
     */
    val calendarEventTypeMap = mapOf(
        "CET_CHALLENGE" to "待办事项",
        "CET_REWARD" to "大奖！",
        "CET_UPGRADE" to "覆写"
    )

    /**
     * 1999 角色生日映射（年日 → 角色名）
     */
    val calendarBirthdayMap = mapOf(
        45 to "莱蒂西娅",     // 2月14日
        143 to "阿米尔",      // 5月23日
        191 to "碧",          // 7月10日
        306 to "埃莉诺",      // 11月2日
        307 to "亚瑟",        // 11月3日
        338 to "昆西"         // 12月4日
    )


    fun String.replaceFaction(): String {
        return factionReplacements.entries.fold(this) { acc: String, entry: Map.Entry<String, String> ->
            acc.replace(entry.key, entry.value)
        }
    }
}