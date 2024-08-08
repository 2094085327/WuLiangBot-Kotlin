package bot.demo.txbot.warframe

import bot.demo.txbot.common.utils.HttpUtil
import bot.demo.txbot.common.utils.OtherUtil.STConversion.turnZhHans
import bot.demo.txbot.common.utils.WebImgUtil
import bot.demo.txbot.warframe.WfStatusController.WfStatus.archonHuntEntity
import bot.demo.txbot.warframe.WfStatusController.WfStatus.fissureList
import bot.demo.txbot.warframe.WfStatusController.WfStatus.incarnonEntity
import bot.demo.txbot.warframe.WfStatusController.WfStatus.invasionsEntity
import bot.demo.txbot.warframe.WfStatusController.WfStatus.nightWaveEntity
import bot.demo.txbot.warframe.WfStatusController.WfStatus.replaceFaction
import bot.demo.txbot.warframe.WfStatusController.WfStatus.replaceTime
import bot.demo.txbot.warframe.WfStatusController.WfStatus.sortieEntity
import bot.demo.txbot.warframe.WfStatusController.WfStatus.steelPathEntity
import bot.demo.txbot.warframe.WfStatusController.WfStatus.voidTraderEntity
import bot.demo.txbot.warframe.WfUtil.WfUtilObject.toEastEightTimeZone
import bot.demo.txbot.warframe.database.WfLexiconService
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.mikuac.shiro.annotation.AnyMessageHandler
import com.mikuac.shiro.annotation.MessageHandlerFilter
import com.mikuac.shiro.annotation.common.Shiro
import com.mikuac.shiro.core.Bot
import com.mikuac.shiro.dto.event.message.AnyMessageEvent
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.io.File
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.*
import java.util.regex.Pattern


/**
 * @description: Warframe 世界状态
 * @author Nature Zero
 * @date 2024/6/9 上午12:35
 */
@Shiro
@Component
class WfStatusController @Autowired constructor(private val webImgUtil: WebImgUtil, private val wfUtil: WfUtil) {

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

    data class NightWaveChallenges(
        val title: String,
        val desc: String,
        val reputation: Int,
        val isDaily: Boolean
    )

    data class NightWaveEntity(
        val activation: String,
        val startString: String,
        val expiry: String,
        val expiryString: String,
        val activeChallenges: List<NightWaveChallenges>
    )

    data class Invasions(
        val itemString: String,
        val factions: String,
    )

    data class InvasionsEntity(
        val node: String,
        val invasionsDetail: List<Invasions>,
        val completion: Double
    )

    data class IncarnonEntity(
        val thisWeekData: WfUtil.Data,
        val nextWeekData: WfUtil.Data,
        val remainTime: String
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

        var nightWaveEntity: NightWaveEntity? = null

        var invasionsEntity = mutableListOf<InvasionsEntity>()

        var incarnonEntity: IncarnonEntity? = null
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
            url = "http://localhost:${webImgUtil.usePort}/warframe/fissureList",
            imgName = "fissureList-${UUID.randomUUID()}",
            element = "body"
        )

        webImgUtil.sendNewImage(bot, event, imgData)
        webImgUtil.deleteImgByQiNiu(imgData = imgData)
    }


    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "\\b(裂缝|裂隙)\\b")
    fun getOrdinaryFissures(bot: Bot, event: AnyMessageEvent) {
        val fissuresJson = HttpUtil.doGetJson(WARFRAME_STATUS_FISSURES, params = mapOf("language" to "zh"))
        val filteredFissures = fissuresJson.filter { eachJson ->
            !eachJson["isStorm"].booleanValue() && !eachJson["isHard"].booleanValue()
        }
        getSendFissureList(bot, event, filteredFissures)
    }

    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "\\b(钢铁裂缝|钢铁裂隙)\\b")
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
                url = "http://localhost:${webImgUtil.usePort}/warframe/voidTrader",
                imgName = "voidTrader-${UUID.randomUUID()}",
                element = "body"
            )

            webImgUtil.sendNewImage(bot, event, imgData)
            webImgUtil.deleteImgByQiNiu(imgData = imgData)
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
            url = "http://localhost:${webImgUtil.usePort}/warframe/steelPath",
            imgName = "steelPath-${UUID.randomUUID()}",
            element = "body"
        )

        webImgUtil.sendNewImage(bot, event, imgData)
        webImgUtil.deleteImgByQiNiu(imgData = imgData)
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
            url = "http://localhost:${webImgUtil.usePort}/warframe/sortie",
            imgName = "sortie-${UUID.randomUUID()}",
            element = "body"
        )

        webImgUtil.sendNewImage(bot, event, imgData)
        webImgUtil.deleteImgByQiNiu(imgData = imgData)
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
            url = "http://localhost:${webImgUtil.usePort}/warframe/archonHunt",
            imgName = "archonHuntInfo-${UUID.randomUUID()}",
            element = "body"
        )

        webImgUtil.sendNewImage(bot, event, imgData)
        webImgUtil.deleteImgByQiNiu(imgData = imgData)
    }

    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "\\b(电波|午夜电波)\\b")
    fun getNightWave(bot: Bot, event: AnyMessageEvent) {
        val nightWaveJson = HttpUtil.doGetJson(WARFRAME_STATUS_NIGHT_WAVE, params = mapOf("language" to "zh"))

        val activation = nightWaveJson["activation"].textValue().replace("T", " ").replace(".000Z", "")
        val expiryString = nightWaveJson["expiry"].textValue().replace("T", " ").replace(".000Z", "")

        // 定义时间格式化器
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

        // 将时间字符串解析为 LocalDateTime 对象
        val nowTime = LocalDateTime.now()
        val endTime = LocalDateTime.parse(expiryString, formatter)

        // 计算时间差
        val duration: Duration = Duration.between(nowTime, endTime)


        // 获取时间差的月数
        val months = (endTime.monthValue - nowTime.monthValue).toLong()
        // 获取时间差的天数
        val days = duration.toDays() - months * 30 // 减去月份的天数
        // 获取剩余的小时数
        val hours = duration.toHours() % 24
        // 获取剩余的分钟数
        val minutes = duration.toMinutes() % 60

        // 格式化时间差
        var timeDifference = ""
        if (months > 0) timeDifference += months.toString() + "个月"
        if (days > 0) timeDifference += days.toString() + "天"
        if (hours > 0) timeDifference += hours.toString() + "小时"
        if (minutes > 0) timeDifference += minutes.toString() + "分钟"

        nightWaveEntity = NightWaveEntity(
            activation = activation,
            startString = nightWaveJson["startString"].textValue().replaceTime().replace("-", ""),
            expiry = expiryString,
            expiryString = timeDifference,
            activeChallenges = nightWaveJson["activeChallenges"].map { item ->
                NightWaveChallenges(
                    title = item["title"].textValue().turnZhHans(),
                    desc = item["desc"].textValue().turnZhHans(),
                    reputation = item["reputation"].intValue(),
                    isDaily = item["isDaily"].booleanValue()
                )
            }
        )

        val imgData = WebImgUtil.ImgData(
            url = "http://localhost:${webImgUtil.usePort}/warframe/nightWave",
            imgName = "nightWave-${UUID.randomUUID()}",
            element = "body"
        )

        webImgUtil.sendNewImage(bot, event, imgData)
        webImgUtil.deleteImgByQiNiu(imgData = imgData)
    }

    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "\\b(火卫二状态|火星状态|火星平原状态|火卫二平原状态|火卫二平原|火星平原)\\b")
    fun phobosStatus(bot: Bot, event: AnyMessageEvent) {
        val phobosStatusJson = HttpUtil.doGetJson(WARFRAME_STATUS_PHOBOS_STATUS, params = mapOf("language" to "zh"))
        val activation = phobosStatusJson["activation"].textValue().toEastEightTimeZone()
        val expiry = phobosStatusJson["expiry"].textValue().toEastEightTimeZone()
        val timeLeft = phobosStatusJson["timeLeft"].textValue().replaceTime()
        val state = phobosStatusJson["state"].textValue()

        bot.sendMsg(
            event, "当前火卫二平原的状态为:${state}\n" +
                    "开始时间:${activation}\n" +
                    "结束时间:${expiry}\n" +
                    "剩余:${timeLeft}", false
        )
    }

    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "\\b(地球状态|地球平原状态|希图斯状态|夜灵平原状态|地球平原|夜灵平原)\\b")
    fun cetusCycle(bot: Bot, event: AnyMessageEvent) {
        val cetusStatusJson = HttpUtil.doGetJson(WARFRAME_STATUS_CETUS_STATUS, params = mapOf("language" to "zh"))
        val activation = cetusStatusJson["activation"].textValue().toEastEightTimeZone()
        val expiry = cetusStatusJson["expiry"].textValue().toEastEightTimeZone()
        val timeLeft = cetusStatusJson["timeLeft"].textValue().replaceTime()
        val state = cetusStatusJson["state"].textValue()
        val stateMap = mapOf("night" to "夜晚", "day" to "白天")

        bot.sendMsg(
            event, "当前地球平原为 ${stateMap[state]} \n" +
                    "开始时间:${activation}\n" +
                    "结束时间:${expiry}\n" +
                    "剩余:${timeLeft}", false
        )
    }

    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "\\b入侵\\b")
    fun invasions(bot: Bot, event: AnyMessageEvent) {
        invasionsEntity.clear()
        val invasionsArray = HttpUtil.doGetJson(WARFRAME_STATUS_INVASIONS, params = mapOf("language" to "zh"))
        invasionsArray.forEach { invasionsJson ->
            if (!invasionsJson["completed"].booleanValue()) {
                invasionsEntity.add(
                    InvasionsEntity(
                        node = invasionsJson["node"].textValue().turnZhHans(),
                        invasionsDetail = listOf(
                            Invasions(
                                itemString = if (invasionsJson["attacker"]["faction"].textValue() == "Infested") "无" else invasionsJson["attacker"]["reward"]["itemString"].textValue()
                                    .turnZhHans(),
                                factions = invasionsJson["attacker"]["faction"].textValue().replaceFaction()
                            ),
                            Invasions(
                                itemString = if (invasionsJson["defender"]["faction"].textValue() == "Infested") "无" else invasionsJson["defender"]["reward"]["itemString"].textValue()
                                    .turnZhHans(),
                                factions = invasionsJson["defender"]["faction"].textValue().replaceFaction()
                            )
                        ),
                        completion = invasionsJson["completion"].doubleValue()
                    )
                )
            }
        }

        val imgData = WebImgUtil.ImgData(
            url = "http://localhost:${webImgUtil.usePort}/warframe/invasions",
            imgName = "invasions-${UUID.randomUUID()}",
            element = "body"
        )

        webImgUtil.sendNewImage(bot, event, imgData)
        webImgUtil.deleteImgByQiNiu(imgData = imgData)
        System.gc()
    }

    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "\\b(本周灵化|这周灵化|灵化|回廊|钢铁回廊|本周回廊)\\b")
    fun incarnon(bot: Bot, event: AnyMessageEvent) {
        val mapper = jacksonObjectMapper()
        val jsonFile = File(WARFRAME_INCARNON)
        // 读取并解析 JSON 文件
        val data: WfUtil.Data = mapper.readValue(jsonFile, WfUtil.Data::class.java)
        // 当前日期
        val currentTime = LocalDateTime.now(ZoneId.of("Asia/Shanghai"))

        val oneWeekLater = currentTime.plusWeeks(1)

        // 更新 week 的 startTime
        val updatedOrdinaryWeeks = wfUtil.updateWeeks2(data, currentTime)

        if (data != updatedOrdinaryWeeks) mapper.writeValue(jsonFile, updatedOrdinaryWeeks)

        // 查找当前周的数据
        val currentOrdinaryWeek = wfUtil.findCurrentWeek(data.ordinary, currentTime)
        val currentSteelWeek = wfUtil.findCurrentWeek(data.steel, currentTime)

        val nextOrdinaryWeek = wfUtil.findCurrentWeek(data.ordinary, oneWeekLater)
        val nextSteelWeek = wfUtil.findCurrentWeek(data.steel, oneWeekLater)
        if (currentOrdinaryWeek == null || currentSteelWeek == null || nextOrdinaryWeek == null || nextSteelWeek == null) {
            bot.sendMsg(event, "啊哦~本周灵化数据不见了", false)
            return
        }

        // 获取下周一的日期
        val nextMonday = currentTime.with(TemporalAdjusters.next(DayOfWeek.MONDAY))

        // 设置时间为下周一早上8点
        val nextMondayAt8 = nextMonday.withHour(8).withMinute(0).withSecond(0).withNano(0)

        // 计算时间差
        val duration = Duration.between(currentTime, nextMondayAt8)

        val days = duration.toDays()
        val hours = duration.toHours() % 24
        val minutes = duration.toMinutes() % 60
        val seconds = duration.toSeconds() % 60

        incarnonEntity = IncarnonEntity(
            thisWeekData = WfUtil.Data(
                ordinary = listOf(currentOrdinaryWeek),
                steel = listOf(currentSteelWeek)
            ),
            nextWeekData = WfUtil.Data(
                ordinary = listOf(nextOrdinaryWeek),
                steel = listOf(nextSteelWeek)
            ),
            remainTime = "$days 天 $hours 小时 $minutes 分钟 $seconds 秒"
        )

        val imgData = WebImgUtil.ImgData(
            url = "http://localhost:${webImgUtil.usePort}/warframe/incarnon",
            imgName = "incarnon-${UUID.randomUUID()}",
            element = "body"
        )

        webImgUtil.sendNewImage(bot, event, imgData)
        webImgUtil.deleteImgByQiNiu(imgData = imgData)
        System.gc()
    }
}