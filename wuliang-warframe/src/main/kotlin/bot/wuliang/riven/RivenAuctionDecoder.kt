package bot.wuliang.riven

import bot.wuliang.entity.vo.WfMarketVo
import bot.wuliang.utils.paginate
import com.fasterxml.jackson.databind.JsonNode
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.math.absoluteValue

sealed interface RivenAuctionDecodeResult {
    data class Success(val value: WfMarketVo.RivenOrderList) : RivenAuctionDecodeResult
    data object Empty : RivenAuctionDecodeResult
}

/** 将 v1 拍卖响应转换为稳定的展示 DTO，并使用 v2 属性目录补齐名称及单位。 */
@Component
class RivenAuctionDecoder(
    private val attributeCatalog: RivenAttributeCatalog,
) {
    /**
     * 按洗次数筛选拍卖并按卖家状态排序，分页后补齐属性名称、单位和展示时间。
     *
     * @param rivenJson Market 拍卖接口原始响应。
     * @param itemZhName 用于结果展示的中文物品名称。
     * @param reRollTimes 洗次数，为 null 时不限制。
     * @param page 请求页码，从一开始。
     * @return 页面结果；筛选后没有拍卖时返回 Empty。
     */
    fun decode(rivenJson: JsonNode, itemZhName: String, reRollTimes: Int?, page: Int = 1): RivenAuctionDecodeResult {
        val matchingOrders = rivenJson["payload"]["auctions"].asSequence()
            .filter { reRollTimes == null || it["item"]["re_rolls"].intValue() == reRollTimes }
            .sortedBy { statusOrder(it["owner"]["status"].textValue()) }
            .toList()
        if (matchingOrders.isEmpty()) return RivenAuctionDecodeResult.Empty
        val selectedPage = matchingOrders.paginate(page, MarketDefaults.AUCTION_PAGE_SIZE)
        val now = Instant.now()

        val definitions = attributeCatalog.findBySlugs(
            selectedPage.items.flatMap { order ->
                order["item"]["attributes"].mapNotNull { it["url_name"]?.textValue() }
            }.distinct()
        )
        val orders = selectedPage.items.map { order ->
            val attributes = order["item"]["attributes"].map { attribute ->
                val slug = attribute["url_name"].textValue()
                val definition = definitions[slug]
                val value = attribute["value"].doubleValue()
                WfMarketVo.Attributes(
                    value = value,
                    positive = attribute["positive"].booleanValue(),
                    slug = slug,
                    displayName = definition?.zhName?.ifBlank { definition.enName } ?: slug,
                    unit = definition?.unit,
                    formattedValue = formatAttributeValue(value, definition?.unit),
                )
            }
            val status = order["owner"]["status"].textValue()
            WfMarketVo.RivenOrderInfo(
                user = order["owner"]["ingame_name"].textValue(),
                userStatus = displayStatus(status),
                modName = capitalizeName(
                    order["item"]["weapon_url_name"].textValue() + " " + order["item"]["name"].textValue()
                ),
                modRank = order["item"]["mod_rank"].intValue(),
                reRolls = order["item"]["re_rolls"].intValue(),
                masteryLevel = order["item"]["mastery_level"].intValue(),
                startPlatinum = order["starting_price"]?.intValue() ?: order["buyout_price"].intValue(),
                buyOutPlatinum = order["buyout_price"]?.intValue() ?: order["starting_price"].intValue(),
                polarity = POLARITY_SYMBOLS[order["item"]["polarity"].textValue()] ?: DEFAULT_POLARITY,
                positive = attributes.filter { it.positive },
                negative = attributes.filterNot { it.positive },
                updateTime = timeAgo(order["updated"].textValue(), now),
            )
        }
        return RivenAuctionDecodeResult.Success(
            WfMarketVo.RivenOrderList(
                itemName = itemZhName,
                orderList = orders,
                currentPage = selectedPage.currentPage,
                totalPages = selectedPage.totalPages,
            )
        )
    }

    /** 根据属性单位生成数值文本，非整数保留至两位小数并去除末尾零，未知单位按原值展示。 */
    private fun formatAttributeValue(value: Double, unit: String?): String {
        val number = if (value.absoluteValue % 1.0 == 0.0) {
            value.toLong().toString()
        } else {
            String.format(Locale.ROOT, "%.2f", value).trimEnd('0').trimEnd('.')
        }
        // v2 的 unit 是格式化依据；未知单位仍保留原值，避免静默丢失上游信息。
        return when (unit) {
            "percent" -> "$number%"
            "multiply" -> "${number}x"
            "seconds" -> "${number}秒"
            null, "" -> number
            else -> "$number $unit"
        }
    }

    /** 将卖家状态映射为排序优先级：游戏中、在线、离线、未知。 */
    private fun statusOrder(status: String): Int = when (status) {
        MarketUserStatuses.IN_GAME -> 0
        MarketUserStatuses.ONLINE -> 1
        MarketUserStatuses.OFFLINE -> 2
        else -> 3
    }

    /** 将 Market 卖家状态转换为中文展示文本，未识别的状态按离线展示。 */
    private fun displayStatus(status: String): String = when (status) {
        MarketUserStatuses.IN_GAME -> "游戏中"
        MarketUserStatuses.ONLINE -> "游戏在线"
        else -> "离线"
    }

    /** 按空格和连字符拆分名称，将各部分首字母转为大写后以空格连接。 */
    private fun capitalizeName(value: String): String = value.split(" ", "-")
        .joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }

    /** 解析 ISO 偏移时间 [value]，根据 [now] 生成相对更新时间文本。 */
    private fun timeAgo(value: String, now: Instant): String {
        val duration = Duration.between(
            OffsetDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant(),
            now,
        )
        return when {
            duration.toDays() >= 365 -> "${duration.toDays() / 365}年前"
            duration.toDays() >= 30 -> "${duration.toDays() / 30}个月前"
            duration.toDays() > 0 -> "${duration.toDays()}天前"
            duration.toHours() > 0 -> "${duration.toHours()}小时前"
            duration.toMinutes() > 0 -> "${duration.toMinutes()}分钟前"
            duration.seconds > 0 -> "${duration.seconds}秒前"
            else -> "刚刚"
        }
    }

    companion object {
        private const val DEFAULT_POLARITY = "-"
        private val POLARITY_SYMBOLS = mapOf("madurai" to "r", "vazarin" to "Δ", "naramon" to "一")
    }
}