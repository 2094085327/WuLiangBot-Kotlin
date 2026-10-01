package bot.wuliang.translation

/** 为派系字段提供简称 */
internal object FactionDisplayNames {
    private val aliases = mapOf(
        "G系" to listOf("Grineer", "FC_GRINEER", "/Lotus/Language/Game/Faction_GrineerUC"),
        "C系" to listOf("Corpus", "FC_CORPUS", "/Lotus/Language/Game/Faction_CorpusUC"),
        "I系" to listOf("Infested", "Infestation", "FC_INFESTATION", "/Lotus/Language/Game/Faction_InfestationUC"),
        "奥罗金" to listOf("Orokin", "奥罗金", "FC_OROKIN", "/Lotus/Language/Game/Faction_OrokinUC"),
        "低语者" to listOf("The Murmur", "低语者", "FC_MITW", "FC_MURMUR", "/Lotus/Language/Game/Faction_MITW"),
        "合一众" to listOf("Narmer", "FC_NARMER", "/Lotus/Language/Game/Faction_NarmerUC"),
        "多方交战" to listOf("Crossfire", "/Lotus/Language/Missions/MissionName_Crossfire"),
        "双方交战" to listOf("/Lotus/Language/Missions/DualDefenseCompare"),
    ).flatMap { (label, keys) -> (keys + label).map { PublicExportIndex.normalize(it) to label } }.toMap()

    fun name(key: String): String? = aliases[PublicExportIndex.normalize(key)]
}