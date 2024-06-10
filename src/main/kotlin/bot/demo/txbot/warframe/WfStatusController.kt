package bot.demo.txbot.warframe

import bot.demo.txbot.common.utils.HttpUtil
import bot.demo.txbot.common.utils.OtherUtil.STConversion.turnZhHans
import bot.demo.txbot.warframe.WfStatusController.WfStatus.replaceFaction
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

    /**
     * 突击任务信息
     *
     * @property missionType 任务类型
     * @property modifier 敌方强化
     * @property node 任务地点
     */
    data class Variants(
        val missionType: String,
        val modifier: String,
        val node: String,
    )

    /**
     * 执刑官任务信息
     *
     * @property node 任务地点
     * @property type 任务类型
     */
    data class Missions(
        val node: String,
        val type: String,
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

        private val factionReplacements = mapOf(
            "Grineer" to "G系",
            "Corpus" to "C系",
            "Infested" to "I系",
            "Infestation" to "I系",
            "Orokin" to "O系",
            "Crossfire" to "多方交战",
            "The Murmur" to "M系",
            "Narmer" to "合一众"
        )


        fun String.replaceTime(): String {
            return timeReplacements.entries.fold(this) { acc: String, entry: Map.Entry<String, String> ->
                acc.replace(entry.key, entry.value)
            }
        }

        fun String.replaceFaction(): String {
            return factionReplacements.entries.fold(this) { acc: String, entry: Map.Entry<String, String> ->
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
                enemyKey = it["enemyKey"].textValue().replaceFaction()
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

        val startString = traderJson["startString"].asText().replaceTime()
        val endString = traderJson["endString"].asText().replaceTime()
        val location = traderJson["location"].asText().turnZhHans()

        if (traderJson["inventory"].isEmpty) {
            bot.sendMsg(
                event,
                "虚空商人仍未回归...\n也许将在 $startString 后抵达 $location",
                false
            )
        } else {
            val itemList = traderJson["inventory"].map { item ->
                VoidTraderItem(
                    item = item["item"].asText(),
                    ducats = item["ducats"].asInt(),
                    credits = item["credits"].asInt()
                )
            }

            val itemsText = itemList.joinToString("\n") { "${it.item} ${it.ducats} 杜卡德 ${it.credits} 现金" }
            bot.sendMsg(event, "虚空商人带来了这些物品:\n$itemsText\n将在 $endString 后离开", false)
        }
    }


    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "钢铁")
    fun getSteelPath(bot: Bot, event: AnyMessageEvent) {
        val steelPath = HttpUtil.doGetJson(WARFRAME_STATUS_STEEL_PATH, params = mapOf("language" to "zh"))

        val currentReward = steelPath["currentReward"]
        val currentName = currentReward["name"].asText()
        val currentCost = currentReward["cost"].asInt()

        // 寻找下一个奖励
        val rotation = steelPath["rotation"]
        val currentIndex = rotation.indexOfFirst { it["name"].asText() == currentName }
        val nextReward = if (currentIndex != -1 && currentIndex < rotation.size() - 1) {
            rotation[currentIndex + 1]
        } else {
            rotation[0]
        }

        // 获取下一个奖励
        val nextName = nextReward["name"]?.asText()
        val nextCost = nextReward["cost"]?.asInt()

        val remaining = steelPath["remaining"].asText().replaceTime()

        val message = """
        钢铁之路的情况如下:
        本周可兑换的限时奖励: $currentName - ${currentCost}精华
        兑换剩余时间: $remaining
        下周奖励: $nextName - ${nextCost}精华
    """.trimIndent()

        bot.sendMsg(event, message, false)
    }

    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "突击")
    fun getSortie(bot: Bot, event: AnyMessageEvent) {
        val sortieJson = HttpUtil.doGetJson(WARFRAME_STATUS_SORTIE, params = mapOf("language" to "zh"))

        val variantsList = sortieJson["variants"]
        val taskList = variantsList.map { item ->
            Variants(
                missionType = item["missionType"].asText().turnZhHans(),
                modifier = item["modifier"].asText().turnZhHans(),
                node = item["node"].asText().turnZhHans()
            )
        }

        val faction = sortieJson["factionKey"].asText().replaceFaction()
        val boss = sortieJson["boss"].asText()
        val eta = sortieJson["eta"].asText().replaceTime()

        val sortieMessage = buildString {
            append("尊敬的Tenno阁下，这是今天的突击信息:\n")
            append("$faction $boss 给出的任务是:\n")
            taskList.forEachIndexed { index, task ->
                append("任务${index + 1}: ${task.missionType} - ${task.node}\n")
                append("- ${task.modifier}\n")
            }
            append("突击剩余时间: $eta")
        }

        bot.sendMsg(event, sortieMessage, false)
    }

    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "执(?:行|刑)官")
    fun getArchonHunt(bot: Bot, event: AnyMessageEvent) {
        val archonHuntJson = HttpUtil.doGetJson(WARFRAME_STATUS_ARCHON_HUNT, params = mapOf("language" to "zh"))

        val boss = archonHuntJson["boss"].asText().replaceFaction()
        val rewardItem = when (boss) {
            "欺谋狼主" -> "深红源力石"
            "混沌蛇主" -> "琥珀源力石"
            "诡文枭主" -> "蔚蓝源力石"
            else -> "未知"
        }

        val taskList = archonHuntJson["missions"].map { item ->
            Missions(
                node = item["node"].asText().turnZhHans(),
                type = item["type"].asText().turnZhHans()
            )
        }

        val faction = archonHuntJson["factionKey"].asText().replaceFaction()
        val eta = archonHuntJson["eta"].asText().replaceTime()

        val archonHuntMessage = """
        ${faction}首领${boss}带着Tenno通牒[${rewardItem}]来袭:
        | 任务1 (130-135): ${taskList[0].node} ${taskList[0].type}
        | 任务2 (135-140): ${taskList[1].node} ${taskList[1].type}
        | 任务3 (145-150): ${taskList[2].node} ${taskList[2].type}
        剩余时间: $eta
    """.trimIndent()

        bot.sendMsg(event, archonHuntMessage, false)
    }

}