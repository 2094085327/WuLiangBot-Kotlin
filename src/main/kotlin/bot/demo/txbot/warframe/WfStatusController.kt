package bot.demo.txbot.warframe

import bot.demo.txbot.common.botUtil.BotUtils.Context
import bot.demo.txbot.common.utils.HttpUtil
import bot.demo.txbot.common.utils.OtherUtil.STConversion.turnZhHans
import bot.demo.txbot.common.utils.WebImgUtil
import bot.demo.txbot.other.distribute.annotation.AParameter
import bot.demo.txbot.other.distribute.annotation.ActionService
import bot.demo.txbot.other.distribute.annotation.Executor
import bot.demo.txbot.warframe.WfStatusController.WfStatus.archonHuntEntity
import bot.demo.txbot.warframe.WfStatusController.WfStatus.fissureList
import bot.demo.txbot.warframe.WfStatusController.WfStatus.incarnonEntity
import bot.demo.txbot.warframe.WfStatusController.WfStatus.invasionsEntity
import bot.demo.txbot.warframe.WfStatusController.WfStatus.moodSpiralsEntity
import bot.demo.txbot.warframe.WfStatusController.WfStatus.nightWaveEntity
import bot.demo.txbot.warframe.WfStatusController.WfStatus.replaceFaction
import bot.demo.txbot.warframe.WfStatusController.WfStatus.replaceTime
import bot.demo.txbot.warframe.WfStatusController.WfStatus.sortieEntity
import bot.demo.txbot.warframe.WfStatusController.WfStatus.steelPathEntity
import bot.demo.txbot.warframe.WfStatusController.WfStatus.voidTraderEntity
import bot.demo.txbot.warframe.WfUtil.WfUtilObject.toEastEightTimeZone
import bot.demo.txbot.warframe.database.WfLexiconService
import bot.demo.txbot.warframe.vo.WfStatusVo
import bot.demo.txbot.warframe.vo.WfUtilVo
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.io.File
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.*
import java.util.regex.Pattern


/**
 * @description: Warframe 世界状态
 * @author Nature Zero
 * @date 2024/6/9 上午12:35
 */
@Component
@ActionService
class WfStatusController @Autowired constructor(
    private val webImgUtil: WebImgUtil,
    private val wfUtil: WfUtil,
    private val wfLexiconService: WfLexiconService
) {
    private val dateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME

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

        var archonHuntEntity: WfStatusVo.ArchonHuntEntity? = null

        var sortieEntity: WfStatusVo.SortieEntity? = null

        var steelPathEntity: WfStatusVo.SteelPathEntity? = null

        var fissureList: WfStatusVo.FissureList? = null

        var voidTraderEntity: WfStatusVo.VoidTraderEntity? = null

        var nightWaveEntity: WfStatusVo.NightWaveEntity? = null

        var invasionsEntity = mutableListOf<WfStatusVo.InvasionsEntity>()

        var incarnonEntity: WfStatusVo.IncarnonEntity? = null

        var moodSpiralsEntity: WfStatusVo.MoodSpiralsEntity? = null
    }

    /**
     * 获取裂缝信息
     *
     * @param filteredFissures 筛选后的裂缝信息
     * @return 发送内容
     */
    private fun getSendFissureList(context: Context, filteredFissures: List<JsonNode>, type: String) {
        val thisFissureList = WfStatusVo.FissureList()
        val filteredFissuresActive = filteredFissures.filter { fissures ->
            fissures["active"].asBoolean()
        }
        filteredFissuresActive.forEach { fissures ->
            val fissureDetail = WfStatusVo.FissureDetail(
                eta = fissures["eta"].textValue().replaceTime(),
                node = fissures["node"].textValue().turnZhHans(),
                missionType = fissures["missionType"].textValue().turnZhHans(),
                enemyKey = fissures["enemyKey"].textValue().replaceFaction()
            )

            when (fissures["tierNum"].intValue()) {
                1 -> thisFissureList.tierLich.add(fissureDetail)
                2 -> thisFissureList.tierMeso.add(fissureDetail)
                3 -> thisFissureList.tierNeo.add(fissureDetail)
                4 -> thisFissureList.tierAxi.add(fissureDetail)
                5 -> thisFissureList.tierRequiem.add(fissureDetail)
                6 -> thisFissureList.tierOmnia.add(fissureDetail)
            }
        }
        fissureList = thisFissureList
        thisFissureList.fissureType = type

        val imgData = WebImgUtil.ImgData(
            url = "http://localhost:${webImgUtil.usePort}/warframe/fissureList",
            imgName = "fissureList-${UUID.randomUUID()}",
            element = "body"
        )

        webImgUtil.sendNewImage(context, imgData)
        webImgUtil.deleteImg(imgData = imgData)
    }

    @AParameter
    @Executor(action = "\\b(裂缝|裂隙)\\b")
    fun getOrdinaryFissures(context: Context) {
        val fissuresJson = HttpUtil.doGetJson(WARFRAME_STATUS_FISSURES, params = mapOf("language" to "zh"))
        val filteredFissures = fissuresJson.filter { eachJson ->
            !eachJson["isStorm"].booleanValue() && !eachJson["isHard"].booleanValue()
        }
        getSendFissureList(context, filteredFissures, "普通裂缝")
    }

    @AParameter
    @Executor(action = "\\b(钢铁裂缝|钢铁裂隙)\\b")
    fun getHardFissures(context: Context) {
        val fissuresJson = HttpUtil.doGetJson(WARFRAME_STATUS_FISSURES, params = mapOf("language" to "zh"))
        val filteredFissures = fissuresJson.filter { eachJson ->
            !eachJson["isStorm"].booleanValue() && eachJson["isHard"].booleanValue()
        }
        getSendFissureList(context, filteredFissures, "钢铁裂缝")
    }

    @AParameter
    @Executor(action = "九重天")
    fun getEmpyreanFissures(context: Context) {
        val fissuresJson = HttpUtil.doGetJson(WARFRAME_STATUS_FISSURES, params = mapOf("language" to "zh"))
        val filteredFissures = fissuresJson.filter { eachJson ->
            eachJson["isStorm"].booleanValue()
        }
        getSendFissureList(context, filteredFissures, "九重天")
    }

    @AParameter
    @Executor(action = "奸商")
    fun findVoidTrader(context: Context) {
        val traderJson = HttpUtil.doGetJson(WARFRAME_STATUS_VOID_TRADER, params = mapOf("language" to "zh"))

        val startString = traderJson["startString"].asText().replaceTime()
        val endString = traderJson["endString"].asText().replaceTime()
        val location = traderJson["location"].asText().turnZhHans()

        if (traderJson["inventory"].isEmpty) {
            context.sendMsg("虚空商人仍未回归...\n也许将在 $startString 后抵达 $location")
        } else {
            // 定义一个正则表达式用于匹配中文字符
            val chinesePattern = Pattern.compile("[\\u4e00-\\u9fff]+")

            val itemList = traderJson["inventory"].map { item ->
                val regex = Regex("000$")
                WfStatusVo.VoidTraderItem(
                    item = wfLexiconService.getZhName(item["item"].asText()) ?: item["item"].asText(),
                    ducats = item["ducats"].asInt(),
                    credits = regex.replace(item["credits"].asText(), "k")
                )
            }

            // 使用sortedWith根据item是否包含中文进行排序
            val sortedItemList = itemList.sortedWith(compareByDescending {
                chinesePattern.matcher(it.item).find()
            })

            voidTraderEntity = WfStatusVo.VoidTraderEntity(
                time = endString,
                location = location,
                items = sortedItemList
            )

            val imgData = WebImgUtil.ImgData(
                url = "http://localhost:${webImgUtil.usePort}/warframe/voidTrader",
                imgName = "voidTrader-${UUID.randomUUID()}",
                element = "body"
            )

            webImgUtil.sendNewImage(context, imgData)
            webImgUtil.deleteImg(imgData = imgData)
        }
    }

    @AParameter
    @Executor(action = "钢铁")
    fun getSteelPath(context: Context) {
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

        steelPathEntity = WfStatusVo.SteelPathEntity(
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

        webImgUtil.sendNewImage(context, imgData)
        webImgUtil.deleteImg(imgData = imgData)
    }

    @AParameter
    @Executor(action = "突击")
    fun getSortie(context: Context) {
        val sortieJson = HttpUtil.doGetJson(WARFRAME_STATUS_SORTIE, params = mapOf("language" to "zh"))

        val variantsList = sortieJson["variants"]
        val taskList = variantsList.map { item ->
            WfStatusVo.Variants(
                missionType = item["missionType"].asText().turnZhHans(),
                modifier = item["modifier"].asText().turnZhHans(),
                node = item["node"].asText().turnZhHans()
            )
        }

        val faction = sortieJson["factionKey"].asText().replaceFaction()
        val boss = sortieJson["boss"].asText()
        val eta = sortieJson["eta"].asText().replaceTime()

        sortieEntity = WfStatusVo.SortieEntity(
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

        webImgUtil.sendNewImage(context, imgData)
        webImgUtil.deleteImg(imgData = imgData)
    }

    @AParameter
    @Executor(action = "执(?:行|刑)官")
    fun getArchonHunt(context: Context) {
        val archonHuntJson = HttpUtil.doGetJson(WARFRAME_STATUS_ARCHON_HUNT, params = mapOf("language" to "zh"))

        val bosses = arrayOf("欺谋狼主", "混沌蛇主", "诡文枭主")
        val rewards = arrayOf("深红源力石", "琥珀源力石", "蔚蓝源力石")

        val bossIndex = bosses.indexOf(archonHuntJson["boss"].asText().replaceFaction())
        val boss = if (bossIndex != -1) bosses[bossIndex] else "未知"
        val rewardItem = if (bossIndex != -1) rewards[bossIndex] else "未知"
        val nextBoss = if (bossIndex != -1) bosses[(bossIndex + 1) % bosses.size] else "未知"
        val nextRewardItem = if (bossIndex != -1) rewards[(bossIndex + 1) % rewards.size] else "未知"

        val taskList = archonHuntJson["missions"].map { item ->
            WfStatusVo.Missions(
                node = item["node"].asText().turnZhHans(),
                type = item["type"].asText().turnZhHans()
            )
        }

        val faction = archonHuntJson["factionKey"].asText().replaceFaction()
        val eta = archonHuntJson["eta"].asText().replaceTime()

        archonHuntEntity = WfStatusVo.ArchonHuntEntity(
            faction = faction,
            boss = boss,
            eta = eta,
            taskList = taskList,
            rewardItem = rewardItem,
            nextBoss = nextBoss,
            nextRewardItem = nextRewardItem
        )

        val imgData = WebImgUtil.ImgData(
            url = "http://localhost:${webImgUtil.usePort}/warframe/archonHunt",
            imgName = "archonHuntInfo-${UUID.randomUUID()}",
            element = "body"
        )

        webImgUtil.sendNewImage(context, imgData)
        webImgUtil.deleteImg(imgData = imgData)
    }

    @AParameter
    @Executor(action = "\\b(电波|午夜电波)\\b")
    fun getNightWave(context: Context) {
        val nightWaveJson = HttpUtil.doGetJson(WARFRAME_STATUS_NIGHT_WAVE, params = mapOf("language" to "zh"))

        val activation = nightWaveJson["activation"].textValue().replace("T", " ").replace(".000Z", "")
        val expiryString = nightWaveJson["expiry"].textValue().replace("T", " ").replace(".000Z", "")

        // 定义时间格式化器
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

        // 将时间字符串解析为 LocalDateTime 对象
        val nowTime = LocalDateTime.now()
        val endTime = LocalDateTime.parse(expiryString, formatter)
        val timeDifference = StringBuilder()

        // 计算月份
        val months = ChronoUnit.MONTHS.between(nowTime, endTime)
        var tempTime = nowTime.plusMonths(months) // 临时时间用于计算剩余的天数

        if (months > 0) timeDifference.append("${months}个月")

        // 计算剩余的天数（减去已计算的月份）
        val days = ChronoUnit.DAYS.between(tempTime, endTime)
        tempTime = tempTime.plusDays(days) // 更新临时时间，用于计算剩余的小时数

        if (days > 0) timeDifference.append("${days}天")

        // 计算小时
        val hours = ChronoUnit.HOURS.between(tempTime, endTime)
        tempTime = tempTime.plusHours(hours)

        if (hours > 0) timeDifference.append("${hours}小时")

        // 计算分钟
        val minutes = ChronoUnit.MINUTES.between(tempTime, endTime)
        if (minutes > 0) timeDifference.append("${minutes}分钟")

        nightWaveEntity = WfStatusVo.NightWaveEntity(
            activation = activation,
            startString = nightWaveJson["startString"].textValue().replaceTime().replace("-", ""),
            expiry = expiryString,
            expiryString = timeDifference.toString(),
            activeChallenges = nightWaveJson["activeChallenges"].map { item ->
                WfStatusVo.NightWaveChallenges(
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

        webImgUtil.sendNewImage(context, imgData)
        webImgUtil.deleteImg(imgData = imgData)
    }

    @AParameter
    @Executor(action = "\\b(火卫二状态|火星状态|火星平原状态|火卫二平原状态|火卫二平原|火星平原)\\b")
    fun phobosStatus(context: Context) {
        val phobosStatusJson = HttpUtil.doGetJson(WARFRAME_STATUS_PHOBOS_STATUS, params = mapOf("language" to "zh"))
        val activation = phobosStatusJson["activation"].textValue().toEastEightTimeZone()
        val expiry = phobosStatusJson["expiry"].textValue().toEastEightTimeZone()
        val timeLeft = phobosStatusJson["timeLeft"].textValue().replaceTime()
        val state = phobosStatusJson["state"].textValue()

        context.sendMsg(
            "当前火卫二平原的状态为:${state}\n" +
                    "开始时间:${activation}\n" +
                    "结束时间:${expiry}\n" +
                    "剩余:${timeLeft}"
        )
    }

    @AParameter
    @Executor(action = "\\b(地球状态|地球平原状态|希图斯状态|夜灵平原状态|地球平原|夜灵平原)\\b")
    fun cetusCycle(context: Context) {
        val cetusStatusJson = HttpUtil.doGetJson(WARFRAME_STATUS_CETUS_STATUS, params = mapOf("language" to "zh"))
        val activation = cetusStatusJson["activation"].textValue().toEastEightTimeZone()
        val expiry = cetusStatusJson["expiry"].textValue().toEastEightTimeZone()
        val timeLeft = cetusStatusJson["timeLeft"].textValue().replaceTime()
        val state = cetusStatusJson["state"].textValue()
        val stateMap = mapOf("night" to "夜晚", "day" to "白天")

        context.sendMsg(
            "当前地球平原为 ${stateMap[state]} \n" +
                    "开始时间:${activation}\n" +
                    "结束时间:${expiry}\n" +
                    "剩余:${timeLeft}"
        )
    }

    @AParameter
    @Executor(action = "\\b(金星状态|金星平原状态|福尔图娜状态|福尔图娜平原状态|金星平原|福尔图娜)\\b")
    fun venusStatus(context: Context) {
        val venusStatusJson = HttpUtil.doGetJson(WARFRAME_STATUS_VENUS_STATUS, params = mapOf("language" to "zh"))
        val activation = venusStatusJson["activation"].textValue().toEastEightTimeZone()
        val expiry = venusStatusJson["expiry"].textValue().toEastEightTimeZone()
        val timeLeft = venusStatusJson["timeLeft"].textValue().replaceTime()
        val state = venusStatusJson["state"].textValue()
        val stateMap = mapOf("cold" to "寒冷", "warm" to "温暖")

        context.sendMsg(
            "当前金星平原为 ${stateMap[state]} \n" +
                    "开始时间:${activation}\n" +
                    "结束时间:${expiry}\n" +
                    "剩余:${timeLeft}"
        )
    }

    @AParameter
    @Executor(action = "\\b入侵\\b")
    fun invasions(context: Context) {
        invasionsEntity.clear()
        val invasionsArray = HttpUtil.doGetJson(WARFRAME_STATUS_INVASIONS, params = mapOf("language" to "zh"))
        invasionsArray.forEach { invasionsJson ->
            if (!invasionsJson["completed"].booleanValue()) {
                invasionsEntity.add(
                    WfStatusVo.InvasionsEntity(
                        node = invasionsJson["node"].textValue().turnZhHans(),
                        invasionsDetail = listOf(
                            WfStatusVo.Invasions(
                                itemString = if (invasionsJson["attacker"]["faction"].textValue() == "Infested") "无" else invasionsJson["attacker"]["reward"]["itemString"].textValue()
                                    .turnZhHans(),
                                factions = invasionsJson["attacker"]["faction"].textValue().replaceFaction()
                            ),
                            WfStatusVo.Invasions(
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

        webImgUtil.sendNewImage(context, imgData)
        webImgUtil.deleteImg(imgData = imgData)
        System.gc()
    }

    @AParameter
    @Executor(action = "\\b(本周灵化|这周灵化|灵化|回廊|钢铁回廊|本周回廊)\\b")
    fun incarnon(context: Context) {
        val mapper = jacksonObjectMapper()
        val newJsonFile = File(WARFRAME_NEW_INCARNON)
        val jsonFile = if (newJsonFile.exists()) newJsonFile else File(WARFRAME_INCARNON)

        // 读取并解析 JSON 文件
        val data: WfUtil.Data = mapper.readValue(jsonFile, WfUtil.Data::class.java)
        val beforeData = mapper.readValue(jsonFile, WfUtil.Data::class.java)
        // 当前日期
        val currentTime = LocalDateTime.now(ZoneId.of("Asia/Shanghai"))

        val oneWeekLater = currentTime.plusWeeks(1)

        // 更新 week 的 startTime
        val updatedOrdinaryWeeks = wfUtil.updateWeeks(data, currentTime)
        if (beforeData != updatedOrdinaryWeeks) mapper.writeValue(newJsonFile, updatedOrdinaryWeeks)

        // 查找当前周的数据
        val currentOrdinaryWeek = wfUtil.findCurrentWeek(updatedOrdinaryWeeks.ordinary, currentTime)
        val currentSteelWeek = wfUtil.findCurrentWeek(updatedOrdinaryWeeks.steel, currentTime)

        val nextOrdinaryWeek = wfUtil.findCurrentWeek(updatedOrdinaryWeeks.ordinary, oneWeekLater)
        val nextSteelWeek = wfUtil.findCurrentWeek(updatedOrdinaryWeeks.steel, oneWeekLater)

        if (currentOrdinaryWeek == null || currentSteelWeek == null || nextOrdinaryWeek == null || nextSteelWeek == null) {
            context.sendMsg("啊哦~本周灵化数据不见了")
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

        incarnonEntity = WfStatusVo.IncarnonEntity(
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

        webImgUtil.sendNewImage(context, imgData)
        webImgUtil.deleteImg(imgData = imgData)
        System.gc()
    }

    @AParameter
    @Executor(action = "\\b(双衍|双衍平原|双衍状态|双衍平原状态|回廊状态|虚空平原状态|复眠螺旋|复眠螺旋状态|王境状态)\\b")
    fun moodSpirals(context: Context) {
        val newJsonFile = File(WARFRAME_NEW_MOOD_SPIRALS)
        val jsonFile = if (newJsonFile.exists()) newJsonFile else File(WARFRAME_MOOD_SPIRALS)
        val mapper = jacksonObjectMapper()
        var weatherData: WfUtilVo.SpiralsData = mapper.readValue(jsonFile, WfUtilVo.SpiralsData::class.java)
        val weatherDataAfter: WfUtilVo.SpiralsData = mapper.readValue(jsonFile, WfUtilVo.SpiralsData::class.java)
        val currentTime = LocalDateTime.now(ZoneId.of("Asia/Shanghai"))

        weatherData = wfUtil.updateWeathers(weatherData, currentTime)
        val currentWeatherData = wfUtil.findSpiralsCurrentTime(weatherData.wfWeather, currentTime)

        if (currentWeatherData == null) {
            context.sendMsg("啊哦~当前时间没有双衍平原数据，请联系开发者检查")
            return
        }
        if (weatherDataAfter != weatherData) mapper.writeValue(newJsonFile, weatherData)


        val hoursLater = OffsetDateTime.parse(currentWeatherData.startTime, dateTimeFormatter)
            .toLocalDateTime()
            .plusHours(2)
            .plusMinutes(1)

        weatherData = wfUtil.updateWeathers(weatherData, hoursLater)
        val hoursLaterWeatherData = wfUtil.findSpiralsCurrentTime(weatherData.wfWeather, hoursLater)

        if (hoursLaterWeatherData == null) {
            context.sendMsg("啊哦~当前时间没有双衍平原数据，请联系开发者检查")
            return
        }

        // 获取当前和下一个天气的状态
        val currentWeatherState = weatherData.weatherStates[currentWeatherData.stateId]
        val damageType = weatherData.damageTypes[currentWeatherData.dmgStateId]
        val nextWeatherState = weatherData.weatherStates[hoursLaterWeatherData.stateId]

        // 确保状态不为null
        if (currentWeatherState == null || damageType == null || nextWeatherState == null) {
            context.sendMsg("啊哦~双衍平原天气数据出现异常，请联系开发者检查")
            return
        }

        // 计算到下一个天气的剩余时间
        val timeUntilNextWeather = Duration.between(
            currentTime,
            OffsetDateTime.parse(hoursLaterWeatherData.startTime, dateTimeFormatter).toLocalDateTime()
        )
        val timeUntilNextWeatherFormatted = wfUtil.formatDuration(timeUntilNextWeather)

        // 获取当前和下一个天气的 NPC 和排除场所信息
        val (npcList, excludeNpcList) = wfUtil.getNpcLists(weatherData, currentWeatherData.stateId)
        val (excludePlaceList, noExcludePlaceList) = wfUtil.getPlaceLists(weatherData, currentWeatherData.stateId)
        val nextExcludePlaceList =
            weatherData.excludePlaces.filter { it.excludeIds.contains(hoursLaterWeatherData.stateId) }
                .map { it.name }

        // 创建 MoodSpiralsEntity 实例
        moodSpiralsEntity = WfStatusVo.MoodSpiralsEntity(
            currentState = currentWeatherState,
            damageType = damageType,
            npc = npcList,
            excludeNpc = excludeNpcList,
            excludePlace = excludePlaceList,
            noExcludePlace = noExcludePlaceList,
            remainTime = timeUntilNextWeatherFormatted,
            nextState = nextWeatherState,
            nextExcludePlace = nextExcludePlaceList
        )

        // 生成和发送图像
        val imgData = WebImgUtil.ImgData(
            url = "http://localhost:${webImgUtil.usePort}/warframe/spirals",
            imgName = "spirals-${UUID.randomUUID()}",
            element = "body"
        )

        webImgUtil.sendNewImage(context, imgData)
        webImgUtil.deleteImg(imgData = imgData)
        System.gc()
    }

    @AParameter
    @Executor(action = "\\b(平原|全部平原)\\b")
    fun allPlain(context: Context) {
        // TODO 全部平原的整合
        return
    }
}