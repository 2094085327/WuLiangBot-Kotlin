package bot.demo.txbot.warframe

import bot.demo.txbot.common.utils.HttpUtil
import bot.demo.txbot.common.utils.OtherUtil.STConversion.turnZhHans
import bot.demo.txbot.warframe.WfStatusController.WfStatus.replaceTime
import com.fasterxml.jackson.databind.JsonNode
import com.mikuac.shiro.annotation.AnyMessageHandler
import com.mikuac.shiro.annotation.MessageHandlerFilter
import com.mikuac.shiro.annotation.common.Shiro
import com.mikuac.shiro.core.Bot
import com.mikuac.shiro.dto.event.message.AnyMessageEvent
import org.springframework.stereotype.Component

/**
 * @description: Warframe 世界状态
 * @author Nature Zero
 * @date 2024/6/9 上午12:35
 */
@Shiro
@Component
class WfStatusController {
    /**
     * 裂缝信息
     *
     * @property tierLich  古纪
     * @property tierMeso 前纪
     * @property tierNeo 中纪
     * @property tierAxi 后纪
     * @property tierRequiem 安魂
     * @property tierOmnia 全能
     */
    data class FissureList(
        var tierLich: MutableList<FissureDetail> = mutableListOf(),
        var tierMeso: MutableList<FissureDetail> = mutableListOf(),
        var tierNeo: MutableList<FissureDetail> = mutableListOf(),
        var tierAxi: MutableList<FissureDetail> = mutableListOf(),
        var tierRequiem: MutableList<FissureDetail> = mutableListOf(),
        var tierOmnia: MutableList<FissureDetail> = mutableListOf(),
    )

    /**
     * 裂缝详情
     *
     * @property eta 截止时间
     * @property node 地点
     * @property missionType 任务类型
     * @property enemyKey 敌人类型
     */
    data class FissureDetail(
        val eta: String,
        val node: String,
        val missionType: String,
        val enemyKey: String,
    )

    /**
     * 虚空商人货物
     *
     * @property item 物品名
     * @property ducats 杜卡德金币
     * @property credits 现金
     */
    data class VoidTraderItem(
        val item: String,
        val ducats: Int,
        val credits: Int
    )

    object WfStatus {
        private val timeReplacements = mapOf(
            "d " to "天",
            " d" to "天",
            "h " to "小时",
            " h" to "小时",
            "m " to "分",
            " m" to "分",
            "s " to "秒",
            " s" to "秒",
            "s" to "秒"
        )

        fun String.replaceTime(): String {
            return timeReplacements.entries.fold(this) { acc: String, entry: Map.Entry<String, String> ->
                acc.replace(entry.key, entry.value)
            }
        }
    }

    /**
     * 获取裂缝信息
     *
     * @param filteredFissures 筛选后的裂缝信息
     * @return 发送内容
     */
    fun getSendFissureList(filteredFissures: List<JsonNode>): StringBuilder {
        val fissureList = FissureList()
        filteredFissures.forEach {
            val fissureDetail = FissureDetail(
                eta = it["eta"].textValue().replaceTime(),
                node = it["node"].textValue().turnZhHans(),
                missionType = it["missionType"].textValue().turnZhHans(),
                enemyKey = when (it["enemyKey"].textValue()) {
                    "Grineer" -> "G系"
                    "Corpus" -> "C系"
                    "Infested" -> "I系"
                    "Orokin" -> "O系"
                    "Crossfire" -> "多方交战"
                    "The Murmur" -> "M系"
                    else -> "未知"
                }
            )
            when (it["tierNum"].intValue()) {
                1 -> fissureList.tierLich.add(fissureDetail)
                2 -> fissureList.tierMeso.add(fissureDetail)
                3 -> fissureList.tierNeo.add(fissureDetail)
                4 -> fissureList.tierAxi.add(fissureDetail)
                5 -> fissureList.tierRequiem.add(fissureDetail)
                6 -> fissureList.tierOmnia.add(fissureDetail)
            }
        }
        val sendMsgBuilder = StringBuilder()

        fun appendFissures(era: String, details: List<FissureDetail>) {
            if (details.isNotEmpty()) {
                sendMsgBuilder.appendLine("$era:")
                details.forEach { detail ->
                    sendMsgBuilder.appendLine("${detail.enemyKey}${detail.missionType} ${detail.node} 剩余: ${detail.eta}")
                }
                sendMsgBuilder.appendLine() // 添加空行分隔不同纪元
            }
        }

        appendFissures("|古纪(T1)", fissureList.tierLich)
        appendFissures("|前纪(T2)", fissureList.tierMeso)
        appendFissures("|中纪(T3)", fissureList.tierNeo)
        appendFissures("|后纪(T4)", fissureList.tierAxi)
        appendFissures("|安魂(T5)", fissureList.tierRequiem)
        appendFissures("|全能(T6)", fissureList.tierOmnia)

        return sendMsgBuilder
    }


    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "裂缝")
    fun getOrdinaryFissures(bot: Bot, event: AnyMessageEvent) {
        val fissuresJson = HttpUtil.doGetJson(WARFRAME_STATUS_FISSURES, params = mapOf("language" to "zh"))
        val filteredFissures = fissuresJson.filter { eachJson ->
            !eachJson["isStorm"].booleanValue() && !eachJson["isHard"].booleanValue()
        }
        val sendMsgBuilder = getSendFissureList(filteredFissures)
        val sendMsg = sendMsgBuilder.toString().trimMargin()
        bot.sendMsg(event, sendMsg, false)
    }

    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "钢铁裂缝")
    fun getHardFissures(bot: Bot, event: AnyMessageEvent) {
        val fissuresJson = HttpUtil.doGetJson(WARFRAME_STATUS_FISSURES, params = mapOf("language" to "zh"))
        val filteredFissures = fissuresJson.filter { eachJson ->
            !eachJson["isStorm"].booleanValue() && eachJson["isHard"].booleanValue()
        }
        val sendMsgBuilder = getSendFissureList(filteredFissures)
        val sendMsg = sendMsgBuilder.toString().trimMargin()
        bot.sendMsg(event, sendMsg, false)
    }

    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "九重天")
    fun getEmpyreanFissures(bot: Bot, event: AnyMessageEvent) {
        val fissuresJson = HttpUtil.doGetJson(WARFRAME_STATUS_FISSURES, params = mapOf("language" to "zh"))
        val filteredFissures = fissuresJson.filter { eachJson ->
            eachJson["isStorm"].booleanValue()
        }
        val sendMsgBuilder = getSendFissureList(filteredFissures)
        val sendMsg = sendMsgBuilder.toString().trimMargin()
        bot.sendMsg(event, sendMsg, false)
    }

    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "奸商")
    fun findVoidTrader(bot: Bot, event: AnyMessageEvent) {
        val traderJson = HttpUtil.doGetJson(WARFRAME_STATUS_VOID_TRADER, params = mapOf("language" to "zh"))

        val startString = traderJson["startString"].textValue()
        val endString = traderJson["endString"].textValue()
        val location = traderJson["location"].textValue().turnZhHans()

        if (traderJson["inventory"].isEmpty) {
            bot.sendMsg(
                event,
                "虚空商人仍未回归...\n也许将在%s后抵达 %s".format(startString.replaceTime(), location),
                false
            )
        } else {
            val itemList = traderJson["inventory"]
                .map { item ->
                    VoidTraderItem(
                        item = item["item"].textValue(),
                        ducats = item["ducats"].intValue(),
                        credits = item["credits"].intValue()
                    )
                }

            val itemsText = itemList.joinToString("\n") { "${it.item} ${it.ducats} 杜卡德 ${it.credits} 现金" }
            bot.sendMsg(event, "虚空商人带来了这些物品:\n$itemsText\n将在 ${endString.replaceTime()} 后离开", false)
        }
    }
}