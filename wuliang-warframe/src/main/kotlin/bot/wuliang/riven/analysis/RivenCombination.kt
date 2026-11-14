package bot.wuliang.riven.analysis

/** 分析与范围查询共用的正负词条组合。 */
enum class RivenCombination(
    val key: String,
    val notation: String,
    val label: String,
    val positiveCount: Int,
    val negativeCount: Int,
    val positiveMultiplier: Double,
    val negativeMultiplier: Double,
) {
    TWO_POSITIVE("2p", "2+", "二正", 2, 0, 0.99, 0.0),
    TWO_POSITIVE_ONE_NEGATIVE("2p1n", "2+1-", "二正一负", 2, 1, 1.2375, -0.495),
    THREE_POSITIVE("3p", "3+", "三正", 3, 0, 0.75, 0.0),
    THREE_POSITIVE_ONE_NEGATIVE("3p1n", "3+1-", "三正一负", 3, 1, 0.9375, -0.75);

    companion object {
        fun forCounts(positive: Int, negative: Int): RivenCombination? =
            entries.singleOrNull { it.positiveCount == positive && it.negativeCount == negative }
    }
}