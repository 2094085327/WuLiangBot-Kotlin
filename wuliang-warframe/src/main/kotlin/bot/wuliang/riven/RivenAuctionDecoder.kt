package bot.wuliang.riven

import bot.wuliang.entity.vo.WfMarketVo
import com.fasterxml.jackson.databind.JsonNode
import org.springframework.stereotype.Component
import bot.wuliang.utils.paginate
import java.time.Duration
import java.time.OffsetDateTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
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
    fun decode(rivenJson: JsonNode, itemZhName: String, reRollTimes: Int?, page: Int = 1): RivenAuctionDecodeResult {
        val matchingOrders = rivenJson["payload"]["auctions"].asSequence()
            .filter { reRollTimes == null || it["item"]["re_rolls"].intValue() == reRollTimes }
            .sortedBy { statusOrder(it["owner"]["status"].textValue()) }
            .toList()
        if (matchingOrders.isEmpty()) return RivenAuctionDecodeResult.Empty
        val selectedPage = matchingOrders.paginate(page, PAGE_SIZE)

        val definitions = attributeCatalog.findBySlugs(
            selectedPage.items.flatMap { order ->
                order["item"]["attributes"].mapNotNull { it["url_name"]?.textValue() }
            }.distinct()
        )
        val polaritySymbols = mapOf("madurai" to "r", "vazarin" to "Δ", "naramon" to "一")
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
                polarity = polaritySymbols[order["item"]["polarity"].textValue()] ?: "-",
                positive = attributes.filter { it.positive },
                negative = attributes.filterNot { it.positive },
                updateTime = timeAgo(order["updated"].textValue()),
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

    private fun statusOrder(status: String): Int = when (status) {
        "ingame" -> 0
        "online" -> 1
        "offline" -> 2
        else -> 3
    }

    private fun displayStatus(status: String): String = when (status) {
        "ingame" -> "游戏中"
        "online" -> "游戏在线"
        else -> "离线"
    }

    private fun capitalizeName(value: String): String = value.split(" ", "-")
        .joinToString(" ") { word -> word.replaceFirstChar { it.uppercase() } }

    private fun timeAgo(value: String): String {
        val duration = Duration.between(
            OffsetDateTime.parse(value, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant(),
            ZonedDateTime.now().toInstant(),
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
        private const val PAGE_SIZE = 5
    }
}