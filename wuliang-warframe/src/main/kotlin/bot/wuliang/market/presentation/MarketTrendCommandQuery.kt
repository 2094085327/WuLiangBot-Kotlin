package bot.wuliang.market.presentation

data class MarketTrendRequest(
    val item: String,
    val window: String = "90days",
    val rank: Int? = null,
    val maxRank: Boolean = false,
) {
    init {
        require(item.isNotBlank() && item.length <= 128) { "请输入物品名称（最多128字），例如：走势 协议覆盖。" }
        require(window in setOf("90days", "48hours")) { "走势仅支持90天或48小时。" }
        require(rank == null || rank in 0..10) { "等级需为0–10级。" }
        require(!maxRank || rank == null) { "满级和指定等级只能选择一项。" }
    }
}

/** 修饰词支持放在名称两侧，并兼容「走势碎裂满级48H」这种连续输入。 */
object MarketTrendCommandQuery {
    const val ACTION = "(?i)^走势(.*)$"
    const val HELP = "走势 物品名：查看90天成交价格走势\n" +
        "添加「48H / 48小时 / 近期」查看48小时走势；「90天」切回长周期\n" +
        "有等级的物品默认0级，可添加「满级」或「N级」n" +
        "例：「走势 重锤射击」、「走势 碎裂 满级 48H」"

    private const val OPTION = "(?:48小时|48时|48h|近期|90天|90日|满级|[0-9]+级)"
    private val prefix = Regex("^$OPTION", RegexOption.IGNORE_CASE)
    private val suffix = Regex("$OPTION$", RegexOption.IGNORE_CASE)
    private val whole = Regex("^$OPTION$", RegexOption.IGNORE_CASE)

    fun parse(arguments: String): MarketTrendRequest {
        var window: String? = null
        var rank: Int? = null
        var maxRank = false
        fun accept(option: String) {
            when {
                option == "满级" -> {
                    require(!maxRank && rank == null) { "每次只能选择一个等级。" }
                    maxRank = true
                }
                option.endsWith("级") -> {
                    require(!maxRank && rank == null) { "每次只能选择一个等级。" }
                    rank = option.dropLast(1).toIntOrNull()
                    require(rank != null && rank!! in 0..10) { "等级需为0–10级。" }
                }
                else -> {
                    require(window == null) { "每次只能选择一个时间范围：90天或48小时。" }
                    window = if (option.startsWith("90")) "90days" else "48hours"
                }
            }
        }
        // 独立参数也可放在多词英文名之间，名称内的正常空格会保留。
        var name = arguments.trim().split(Regex("\\s+")).filter { token ->
            if (whole.matches(token)) { accept(token); false } else true
        }.joinToString(" ")
        while (name.isNotEmpty()) {
            val match = prefix.find(name) ?: suffix.find(name) ?: break
            accept(match.value)
            name = name.removeRange(match.range).trim()
        }
        return MarketTrendRequest(name, window ?: "90days", rank, maxRank)
    }
}