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

    fun sendMarketItemInfo(bot: Bot, event: AnyMessageEvent, item: WfLexiconEntity) {
        val url = "$WARFRAME_MARKET_ITEMS/${item.urlName}/orders"
        LANGUAGE_ZH_HANS["accept"] = "application/json"
        val marketJson = HttpUtil.doGetJson(url = url, headers = LANGUAGE_ZH_HANS)

        // 定义允许的状态集合
        val allowedStatuses = setOf("online", "ingame")

        // 提取订单列表，筛选符合条件的订单并按platinum值排序
        val markList = marketJson["payload"]["orders"]
            .filter { it["order_type"].textValue() == "sell" && it["user"]["status"].textValue() in allowedStatuses }
            .sortedBy { it["platinum"].intValue() }
            .take(5)

        val ordersList = mutableListOf<OrderInfo>()
        markList.forEach {
            val orderInfo = OrderInfo(
                platinum = it["platinum"].intValue(),
                quantity = it["quantity"].intValue(),
                inGameName = it["user"]["ingame_name"].textValue()
            )
            ordersList.add(orderInfo)
        }

        val orderString = if (ordersList.isEmpty()) {
            "当前没有任何在线的玩家出售${item.zhItemName}"
        } else {
            ordersList.joinToString("\n") {
                "${it.inGameName} 价格: ${it.platinum} 数量: ${it.quantity}"
            }

        }
        bot.sendMsg(
            event,
            "你查询的物品是 ${item.zhItemName}\n" +
                    "$orderString\n" +
                    "/w ${ordersList.first().inGameName} Hi! I want to buy: \"${item.enItemName}\" for ${ordersList.first().platinum} platinum.(warframe market)",
            false
        )
    }

    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "wm (.*)")
    fun getMarketItem(bot: Bot, event: AnyMessageEvent, matcher: Matcher) {
        // TODO 实现查询mod等级
        val key = matcher.group(1)
        val itemEntity = wfLexiconService.turnKeyToUrlNameByLexicon(key)

        if (itemEntity != null) {
            sendMarketItemInfo(bot, event, itemEntity)
            return
        }

        val keyList = wfLexiconService.turnKeyToUrlNameByLexiconLike(key)
        if (keyList.isNullOrEmpty()) {
            bot.sendMsg(event, "未找到该物品", false)
            return
        }

        val item = keyList.last()!!
        sendMarketItemInfo(bot, event, item)
    }
}