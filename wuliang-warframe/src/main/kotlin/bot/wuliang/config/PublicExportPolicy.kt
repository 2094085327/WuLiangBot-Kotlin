package bot.wuliang.config

/** 导出协议与本地快照规则，不属于部署参数。 */
object PublicExportPolicy {
    const val MAX_FILE_BYTES = 64_000_000
    const val MAX_INDEX_BYTES = 1_000_000
    const val LZMA_MEMORY_LIMIT_KIB = 64 * 1024
    const val USER_AGENT = "Wuliang-Public-Export"
    const val OFFICIAL_CACHE_DIRECTORY = "official-cache"
    const val SNAPSHOT_VERSION = "translations-v3"
    const val TRANSLATION_CACHE_VERSION = "official-v1"
    val LOCALES = listOf("en", "zh")
    val SHA_PATTERN = Regex("[0-9a-f]{40}")
    val PLUS_FILE_PATTERN = Regex("Export[A-Za-z]+\\.json|dict\\.(en|zh)\\.json")
    val OFFICIAL_FILE_PATTERN = Regex("official\\.Export[A-Za-z]+\\.(en|zh)\\.json")
    val OFFICIAL_INDEX_PATTERN = Regex("(Export[A-Za-z]+)_(en|zh)\\.json!([A-Za-z0-9_+\\-]+)")
    val SNAPSHOT_DIRECTORY_PATTERN = Regex("[0-9a-f]{40}-[0-9a-f-]{36}")
    val REQUIRED_PLUS_FILES = setOf(
        "dict.en.json", "dict.zh.json", "ExportRegions.json",
        "ExportMissionTypes.json", "ExportFactions.json", "ExportNightwave.json",
    )
    val COMPLETE_LANGUAGE_FILES = LOCALES.map { "languages.$it.json" }.toSet()
    val REQUIRED_OFFICIAL_CATEGORIES = setOf(
        "ExportWeapons", "ExportWarframes", "ExportUpgrades", "ExportResources",
        "ExportRegions", "ExportRecipes", "ExportRelicArcane", "ExportSortieRewards",
    )
}