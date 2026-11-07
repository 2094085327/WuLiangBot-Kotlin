package bot.wuliang.moudles

/** 回廊页面报告。完整轮换只用于接口响应，不写入本周缓存。 */
class IncarnonReport(
    incarnon: Incarnon,
    val steelRotation: List<SteelRotationWeek>,
) {
    val thisWeek = incarnon.thisWeek
    val nextWeek = incarnon.nextWeek
    val activation = incarnon.activation
    val expiry = incarnon.expiry
    val eta = incarnon.eta

    data class SteelRotationWeek(
        val week: Int,
        val items: List<String>,
    )
}