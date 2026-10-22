package bot.wuliang.market.presentation

import bot.wuliang.market.history.MarketItemCategory
import bot.wuliang.market.history.MarketRankSelection
import bot.wuliang.market.history.MarketRankingMetric
import bot.wuliang.market.history.MarketRankingRequest
import java.util.*

/** 中文参数独立于 HTTP 枚举参数。 */
object MarketRankingCommandQuery {
    const val ACTION = "^排行(.*)$"
    const val HELP = "使用指令「排行」来获取总榜，为其添加分类「战甲/武器/武器P/赋能/P卡/MOD」来获取单榜详情\n" +
            "添加副指令改变显示数据「加权/最低/最高/成交量（四选一），满级/0级，反向，-页码，每页条数（1–100，单榜默认10）」\n" +
            "例：排行P卡 满级 反向 最高 每页20 -2。"

    private val categories = mapOf(
        "战甲" to MarketItemCategory.WARFRAME,
        "武器" to MarketItemCategory.WEAPON,
        "武器P" to MarketItemCategory.PRIME_WEAPON,
        "赋能" to MarketItemCategory.ARCANE,
        "P卡" to MarketItemCategory.PRIME_MOD,
        "MOD" to MarketItemCategory.MOD,
    )
    private val metrics = mapOf(
        "加权" to MarketRankingMetric.TRADE_AVERAGE,
        "最低" to MarketRankingMetric.TRADE_MIN_PRICE,
        "最高" to MarketRankingMetric.TRADE_MAX_PRICE,
        "成交量" to MarketRankingMetric.TRADE_VOLUME,
    )

    fun parse(arguments: String): MarketRankingRequest {
        var category: MarketItemCategory? = null
        var categorySpecified = false
        var metric: MarketRankingMetric? = null
        var rank: MarketRankSelection? = null
        var descending = true
        var page: Int? = null
        var pageSize: Int? = null
        for (raw in arguments.trim().split(Regex("\\s+")).filter(String::isNotBlank)) {
            val token = raw.uppercase(Locale.ROOT)
            val selectedCategory = categories[token]
            val selectedMetric = metrics[token]
            when {
                selectedCategory != null || token == "总榜" -> {
                    require(!categorySpecified) { "每次只能选择一个榜单分类。" }
                    category = selectedCategory
                    categorySpecified = true
                }

                selectedMetric != null -> {
                    require(metric == null) { "加权、最低、最高、成交量只能选择一项。" }
                    metric = selectedMetric
                }

                token == "满级" || token == "0级" -> {
                    require(rank == null) { "满级和0级只能选择一项。" }
                    rank = if (token == "满级") MarketRankSelection.MAX else MarketRankSelection.BASE
                }

                token == "反向" -> descending = false
                token.startsWith("每页") -> {
                    require(pageSize == null) { "每次只能指定一个每页条数。" }
                    val value = token.removePrefix("每页").removeSuffix("条")
                    val parsed = value.takeIf { it.matches(Regex("[0-9]+")) }?.toIntOrNull()
                    require(parsed != null && parsed in 1..100) { "每页条数需为1–100，例如：排行战甲 每页20。" }
                    pageSize = parsed
                }

                token.startsWith("-") -> {
                    require(page == null) { "每次只能指定一个页码。" }
                    val value = token.drop(1)
                    val parsed = value.takeIf { it.matches(Regex("[0-9]+")) }?.toIntOrNull()
                    require(parsed != null && parsed in 1..1000) { "页码格式为 -1 至 -1000，例如：排行战甲 -2。" }
                    page = parsed
                }

                else -> throw IllegalArgumentException("未知参数，请发送「排行 帮助」查看用法。")
            }
        }
        require(category != null || (page ?: 1) == 1) { "总榜不分页，请指定分类，例如：排行战甲 -2。" }
        require(category != null || pageSize == null) { "总榜固定各类前三，每页条数仅用于单榜，例如：排行战甲 每页20。" }
        require(
            rank != MarketRankSelection.BASE || category in setOf(
                MarketItemCategory.ARCANE, MarketItemCategory.PRIME_MOD, MarketItemCategory.MOD
            )
        ) { "0级选项用于赋能、P卡和MOD单榜，例如：排行赋能 0级。" }
        return MarketRankingRequest(
            category = category, metric = metric ?: MarketRankingMetric.TRADE_AVERAGE,
            rankSelection = rank, descending = descending, page = page ?: 1, pageSize = pageSize ?: 10,
        )
    }
}