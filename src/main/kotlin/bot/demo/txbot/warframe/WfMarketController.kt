package bot.demo.txbot.warframe

import bot.demo.txbot.common.utils.HttpUtil
import bot.demo.txbot.warframe.database.WfLexiconEntity
import bot.demo.txbot.warframe.database.WfLexiconService
import com.mikuac.shiro.annotation.AnyMessageHandler
import com.mikuac.shiro.annotation.MessageHandlerFilter
import com.mikuac.shiro.annotation.common.Shiro
import com.mikuac.shiro.core.Bot
import com.mikuac.shiro.dto.event.message.AnyMessageEvent
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.util.regex.Matcher


/**
 * @description: Warframe 市场
 * @author Nature Zero
 * @date 2024/5/20 上午9:03
 */
@Shiro
@Component
class WfMarketController {
    @Autowired
    lateinit var wfLexiconService: WfLexiconService

    data class OrderInfo(
        val platinum: Int,
        val quantity: Int,
        val inGameName: String,
    )

    /**
     * 发送物品信息
     *
     * @param bot 机器人
     * @param event 事件
     * @param item 物品
     * @param modLevel 模组等级
     */
    fun sendMarketItemInfo(bot: Bot, event: AnyMessageEvent, item: WfLexiconEntity, modLevel: Any? = null) {
        val url = "$WARFRAME_MARKET_ITEMS/${item.urlName}/orders"
        val headers = mutableMapOf<String, Any>("accept" to "application/json")
        val marketJson = HttpUtil.doGetJson(url = url, headers = headers)

        // 定义允许的状态集合
        val allowedStatuses = setOf("online", "ingame")
        val orders = marketJson["payload"]["orders"]

        // 获取所有订单中的最大 mod_rank 值
        val maxModRank = if (modLevel == "满级" && orders.any { it.has("mod_rank") }) {
            orders.filter { it.has("mod_rank") }.maxOfOrNull { it["mod_rank"].intValue() }
        } else {
            (modLevel as? String)?.toIntOrNull()
        }

        // 筛选出符合条件的订单
        val filteredOrders = orders.asSequence()
            .filter { order ->
                order["order_type"].textValue() == "sell" &&
                        order["user"]["status"].textValue() in allowedStatuses &&
                        (modLevel == null || (order.has("mod_rank") &&
                                order["mod_rank"].intValue() == maxModRank))
            }
            .sortedBy { it["platinum"].intValue() }
            .take(5)
            .map {
                OrderInfo(
                    platinum = it["platinum"].intValue(),
                    quantity = it["quantity"].intValue(),
                    inGameName = it["user"]["ingame_name"].textValue()
                )
            }
            .toList()

        val orderString = if (filteredOrders.isEmpty()) {
            "当前没有任何在线的玩家出售${item.zhItemName}"
        } else {
            filteredOrders.joinToString("\n") {
                "${it.inGameName} 价格: ${it.platinum} 数量: ${it.quantity}"
            } + "\n/w ${filteredOrders.first().inGameName} Hi! I want to buy: \"${item.enItemName}\" for ${filteredOrders.first().platinum} platinum.(warframe market)"
        }

        val modLevelString = when {
            modLevel == "满级" -> "满级"
            modLevel != null -> "${modLevel}级"
            else -> ""
        }

        bot.sendMsg(
            event,
            "你查询的物品是 $modLevelString「${item.zhItemName}」\n$orderString",
            false
        )
    }

    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "wm (.*)")
    fun getMarketItem(bot: Bot, event: AnyMessageEvent, matcher: Matcher) {
        val key = matcher.group(1)
        val regex = """(\d+)(?=级)|(满级)""".toRegex()
        val matchResult = regex.find(key)
        val level = matchResult?.value

        // 移除匹配到的部分
        val cleanKey = if (matchResult != null) {
            key.replace("${matchResult.value}级", "").replace("满级", "").trim()
        } else {
            key
        }
        val itemEntity = wfLexiconService.turnKeyToUrlNameByLexicon(cleanKey)

        if (itemEntity != null) {
            sendMarketItemInfo(bot, event, itemEntity, level)
            return
        }

        val keyList = wfLexiconService.turnKeyToUrlNameByLexiconLike(cleanKey)
        if (keyList.isNullOrEmpty()) {
            bot.sendMsg(event, "未找到该物品", false)
            return
        }

        val item = keyList.last()!!
        sendMarketItemInfo(bot, event, item, level)
    }
}