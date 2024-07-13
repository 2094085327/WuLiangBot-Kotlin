package bot.demo.txbot.warframe

import bot.demo.txbot.common.utils.HttpUtil
import bot.demo.txbot.common.utils.OtherUtil.STConversion.turnZhHans
import bot.demo.txbot.common.utils.WebImgUtil
import bot.demo.txbot.warframe.WfStatusController.WfStatus.archonHuntEntity
import bot.demo.txbot.warframe.WfStatusController.WfStatus.fissureList
import bot.demo.txbot.warframe.WfStatusController.WfStatus.replaceFaction
import bot.demo.txbot.warframe.WfStatusController.WfStatus.replaceTime
import bot.demo.txbot.warframe.WfStatusController.WfStatus.sortieEntity
import bot.demo.txbot.warframe.WfStatusController.WfStatus.steelPathEntity
import bot.demo.txbot.warframe.WfStatusController.WfStatus.voidTraderEntity
import bot.demo.txbot.warframe.database.WfLexiconService
import com.fasterxml.jackson.databind.JsonNode
import com.mikuac.shiro.annotation.AnyMessageHandler
import com.mikuac.shiro.annotation.MessageHandlerFilter
import com.mikuac.shiro.annotation.common.Shiro
import com.mikuac.shiro.core.Bot
import com.mikuac.shiro.dto.event.message.AnyMessageEvent
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.util.regex.Pattern

/**
 * @description: Warframe 世界状态
 * @author Nature Zero
 * @date 2024/6/9 上午12:35
 */
@Shiro
@Component
class WfStatusController {
    val webImgUtil = WebImgUtil()

    @Autowired
    final lateinit var wfLexiconService: WfLexiconService

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
        val credits: String
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

    /**
     * 执刑官突击信息
     *
     * @property faction 阵营
     * @property boss Boss名称
     * @property rewardItem 奖励物品
     * @property taskList 任务列表
     * @property eta 剩余时间
     */
    data class ArchonHuntEntity(
        val faction: String,
        val boss: String,
        val rewardItem: String,
        val taskList: List<Missions>,
        val eta: String
    )

    /**
     * 每日突击信息
     *
     * @property faction 阵营
     * @property boss Boss名称
     * @property taskList 任务列表
     * @property eta 剩余时间
     */
    data class SortieEntity(
        val faction: String,
        val boss: String,
        val taskList: List<Variants>,
        val eta: String
    )

    /**
     * 钢铁之路信息
     *
     * @property currentName 当前可兑换物品名称
     * @property currentCost 当前可兑换物品价格
     * @property remaining 剩余时间
     * @property nextName 下一个可兑换物品名称
     * @property nextCost 下一个可兑换物品价格
     */
    data class SteelPathEntity(
        val currentName: String,
        val currentCost: Int,
        val remaining: String,
        val nextName: String,
        val nextCost: Int
    )

    data class VoidTraderEntity(
        val location: String,
        val time: String,
        val items: List<VoidTraderItem>
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

        var archonHuntEntity: ArchonHuntEntity? = null

        var sortieEntity: SortieEntity? = null

        var steelPathEntity: SteelPathEntity? = null

        var fissureList: FissureList? = null

        var voidTraderEntity: VoidTraderEntity? = null
    }

    /**
     * 获取裂缝信息
     *
     * @param filteredFissures 筛选后的裂缝信息
     * @return 发送内容
     */
    fun getSendFissureList(bot: Bot, event: AnyMessageEvent, filteredFissures: List<JsonNode>) {
        val thisFissureList = FissureList()

        filteredFissures.forEach {
            val fissureDetail = FissureDetail(
                eta = it["eta"].textValue().replaceTime(),
                node = it["node"].textValue().turnZhHans(),
                missionType = it["missionType"].textValue().turnZhHans(),
                enemyKey = it["enemyKey"].textValue().replaceFaction()
            )

            when (it["tierNum"].intValue()) {
                1 -> thisFissureList.tierLich.add(fissureDetail)
                2 -> thisFissureList.tierMeso.add(fissureDetail)
                3 -> thisFissureList.tierNeo.add(fissureDetail)
                4 -> thisFissureList.tierAxi.add(fissureDetail)
                5 -> thisFissureList.tierRequiem.add(fissureDetail)
                6 -> thisFissureList.tierOmnia.add(fissureDetail)
            }
        }
        fissureList = thisFissureList

        val imgData = WebImgUtil.ImgData(
            url = "http://localhost:${WebImgUtil.usePort}/warframe/fissureList",
            imgName = "fissureList",
            element = "body"
        )

        webImgUtil.sendNewImage(bot, event, imgData)
    }


    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "裂缝")
    fun getOrdinaryFissures(bot: Bot, event: AnyMessageEvent) {
        val fissuresJson = HttpUtil.doGetJson(WARFRAME_STATUS_FISSURES, params = mapOf("language" to "zh"))
        val filteredFissures = fissuresJson.filter { eachJson ->
            !eachJson["isStorm"].booleanValue() && !eachJson["isHard"].booleanValue()
        }
        getSendFissureList(bot, event, filteredFissures)
    }

    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "钢铁裂缝")
    fun getHardFissures(bot: Bot, event: AnyMessageEvent) {
        val fissuresJson = HttpUtil.doGetJson(WARFRAME_STATUS_FISSURES, params = mapOf("language" to "zh"))
        val filteredFissures = fissuresJson.filter { eachJson ->
            !eachJson["isStorm"].booleanValue() && eachJson["isHard"].booleanValue()
        }
        getSendFissureList(bot, event, filteredFissures)
    }

    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "九重天")
    fun getEmpyreanFissures(bot: Bot, event: AnyMessageEvent) {
        val fissuresJson = HttpUtil.doGetJson(WARFRAME_STATUS_FISSURES, params = mapOf("language" to "zh"))
        val filteredFissures = fissuresJson.filter { eachJson ->
            eachJson["isStorm"].booleanValue()
        }
        getSendFissureList(bot, event, filteredFissures)
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
            // 定义一个正则表达式用于匹配中文字符
            val chinesePattern = Pattern.compile("[\\u4e00-\\u9fff]+")

            val itemList = traderJson["inventory"].map { item ->
                val regex = Regex("000$")
                VoidTraderItem(
                    item = wfLexiconService.getZhName(item["item"].asText()) ?: item["item"].asText(),
                    ducats = item["ducats"].asInt(),
                    credits = regex.replace(item["credits"].asText(), "k")
                )
            }

            // 使用sortedWith根据item是否包含中文进行排序
            val sortedItemList = itemList.sortedWith(compareByDescending {
                chinesePattern.matcher(it.item).find()
            })

            voidTraderEntity = VoidTraderEntity(
                time = endString,
                location = location,
                items = sortedItemList
            )

            val imgData = WebImgUtil.ImgData(
                url = "http://localhost:${WebImgUtil.usePort}/warframe/voidTrader",
                imgName = "voidTrader",
                element = "body"
            )

            webImgUtil.sendNewImage(bot, event, imgData)
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
        val nextName = nextReward["name"].asText()
        val nextCost = nextReward["cost"].asInt()

        val remaining = steelPath["remaining"].asText().replaceTime()

        steelPathEntity = SteelPathEntity(
            currentName = currentName,
            currentCost = currentCost,
            nextName = nextName,
            nextCost = nextCost,
            remaining = remaining
        )

        val imgData = WebImgUtil.ImgData(
            url = "http://localhost:${WebImgUtil.usePort}/warframe/steelPath",
            imgName = "steelPath",
            element = "body"
        )

        webImgUtil.sendNewImage(bot, event, imgData)
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

        sortieEntity = SortieEntity(
            faction = faction,
            boss = boss,
            eta = eta,
            taskList = taskList
        )

        val imgData = WebImgUtil.ImgData(
            url = "http://localhost:${WebImgUtil.usePort}/warframe/sortie",
            imgName = "sortie",
            element = "body"
        )

        webImgUtil.sendNewImage(bot, event, imgData)
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

        archonHuntEntity = ArchonHuntEntity(
            faction = faction,
            boss = boss,
            eta = eta,
            taskList = taskList,
            rewardItem = rewardItem
        )

        val imgData = WebImgUtil.ImgData(
            url = "http://localhost:${WebImgUtil.usePort}/warframe/archonHunt",
            imgName = "archonHuntInfo",
            element = "body"
        )

        webImgUtil.sendNewImage(bot, event, imgData)
    }

}