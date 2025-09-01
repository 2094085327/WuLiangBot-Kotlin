package bot.wuliang.moudles

/**
 * 裂罅Mod,紫卡
 *
 * @property itemType 裂罅MOD类型
 * @property compatibility 武器名
 * @property rerolled 是否已循环
 * @property avg 紫卡价格平均值
 * @property stddev 紫卡价格标准差
 * @property min 紫卡价格最小值
 * @property max 紫卡价格最大值
 * @property pop 热度
 * @property median 紫卡价格中位数
 */
data class Riven(
    val itemType: String? = null,
    val compatibility: String? = null,
    val rerolled: Boolean? = null,
    val avg: Double? = null,
    val stddev: Double? = null,
    val min: Int? = null,
    val max: Int? = null,
    val pop: Int? = null,
    val median: Double? = null
)
