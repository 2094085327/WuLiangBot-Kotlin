package bot.wuliang.arbitration

/** 满加成生息小时中位数的固定分档 */
enum class ArbitrationTier(val label: String, private val minimum: Double?) {
    S("S", 850.0),
    A_PLUS("A+", 800.0),
    A("A", 750.0),
    A_MINUS("A-", 700.0),
    B("B", 550.0),
    C("C", 400.0),
    UNRATED("未分级", null);

    val highValue: Boolean get() = this == S || this == A_PLUS || this == A

    companion object {
        fun fromMedian(median: Double?): ArbitrationTier {
            if (median == null || !median.isFinite()) return UNRATED
            return entries.firstOrNull { it.minimum != null && median >= it.minimum } ?: UNRATED
        }
    }
}