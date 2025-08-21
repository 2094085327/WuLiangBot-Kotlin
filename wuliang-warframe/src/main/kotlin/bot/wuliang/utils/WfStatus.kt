package bot.wuliang.utils

object WfStatus {
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


    fun String.replaceFaction(): String {
        return factionReplacements.entries.fold(this) { acc: String, entry: Map.Entry<String, String> ->
            acc.replace(entry.key, entry.value)
        }
    }
}