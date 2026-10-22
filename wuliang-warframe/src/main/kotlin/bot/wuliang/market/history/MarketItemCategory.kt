package bot.wuliang.market.history

/** 市场榜单分类。 */
enum class MarketItemCategory(
    val requiredTags: Set<String>,
    val excludedTags: Set<String> = emptySet(),
    /** P 卡使用市场 slug 的 primed_ 前缀区分；prime 标签不代表 Primed MOD。 */
    val primedMod: Boolean? = null,
) {
    ALL(emptySet()),
    WARFRAME(setOf("warframe", "prime", "set")),
    WEAPON(setOf("weapon"), setOf("prime", "component", "blueprint")),
    PRIME_WEAPON(setOf("weapon", "prime", "set")),
    ARCANE(setOf("arcane_enhancement"), setOf("mod")),
    PRIME_MOD(setOf("mod"), primedMod = true),
    MOD(setOf("mod"), primedMod = false);

    fun matches(tags: Set<String>, urlName: String): Boolean =
        tags.containsAll(requiredTags) && tags.intersect(excludedTags).isEmpty() &&
                (primedMod == null || urlName.startsWith("primed_", ignoreCase = true) == primedMod)
}