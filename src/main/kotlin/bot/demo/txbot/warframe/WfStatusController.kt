package bot.demo.txbot.warframe

import bot.demo.txbot.common.botUtil.BotUtils.Context
import bot.demo.txbot.common.utils.HttpUtil
import bot.demo.txbot.common.utils.OtherUtil.STConversion.turnZhHans
import bot.demo.txbot.common.utils.RedisService
import bot.demo.txbot.common.utils.WebImgUtil
import bot.demo.txbot.other.distribute.annotation.AParameter
import bot.demo.txbot.other.distribute.annotation.ActionService
import bot.demo.txbot.other.distribute.annotation.Executor
import bot.demo.txbot.warframe.WfStatusController.WfStatus.parseDuration
import bot.demo.txbot.warframe.WfStatusController.WfStatus.replaceFaction
import bot.demo.txbot.warframe.WfStatusController.WfStatus.replaceTime
import bot.demo.txbot.warframe.database.WfLexiconService
import bot.demo.txbot.warframe.vo.WfStatusVo
import bot.demo.txbot.warframe.vo.WfUtilVo
import bot.demo.txbot.warframe.warframeResp.WarframeRespEnum
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
import java.util.concurrent.TimeUnit
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
    private val wfLexiconService: WfLexiconService,
    private val redisService: RedisService
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

        fun String.parseDuration(): Long {
            // 定义正则表达式，匹配天数、小时数、分钟数和秒数
            val regex = Regex("(?:(\\d+)天)?(?:(\\d+)小时)?(?:(\\d+)分)?(?:(\\d+)秒)?")

            // 使用正则表达式进行匹配
            val matchResult = regex.find(this) ?: return 0L

            // 提取匹配结果
            val (days, hours, minutes, seconds) = matchResult.destructured

            // 将提取的字符串转换为整数，并处理可能的空字符串情况
            val day = days.toIntOrNull() ?: 0
            val hour = hours.toIntOrNull() ?: 0
            val minute = minutes.toIntOrNull() ?: 0
            val second = seconds.toIntOrNull() ?: 0

            // 计算总秒数
            return day * TimeUnit.DAYS.toSeconds(1) +
                    hour * TimeUnit.HOURS.toSeconds(1) +
                    minute * TimeUnit.MINUTES.toSeconds(1) +
                    second * TimeUnit.SECONDS.toSeconds(1)
        }
    }

    /**
     * 获取裂缝信息
     *
     * @param filteredFissures 筛选后的裂缝信息
     * @return 发送内容
     */
    private fun getSendFissureList(filteredFissures: List<JsonNode>, type: String) {
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
        thisFissureList.fissureType = type
        val typeMap = mapOf("普通裂缝" to "ordinary", "钢铁裂缝" to "hard", "九重天" to "empyrean")
        redisService.setValueWithExpiry("warframe:${typeMap[type]}", thisFissureList, 1L, TimeUnit.MINUTES)
    }

    @AParameter
    @Executor(action = "\\b(裂缝|裂隙)\\b")
    fun getOrdinaryFissures(context: Context) {
        if (!redisService.hasKey("warframe:ordinary")) {
            val fissuresJson = HttpUtil.doGetJson(WARFRAME_STATUS_FISSURES, params = mapOf("language" to "zh"))
            val filteredFissures = fissuresJson.filter { eachJson ->
                !eachJson["isStorm"].booleanValue() && !eachJson["isHard"].booleanValue()
            }
            getSendFissureList(filteredFissures, "普通裂缝")
        }
        val imgData = WebImgUtil.ImgData(
            url = "http://localhost:16666/fissureList?type=ordinary",
            imgName = "fissureList-${UUID.randomUUID()}",
            element = "body"
        )

        webImgUtil.sendNewImage(context, imgData)
        webImgUtil.deleteImg(imgData = imgData)
    }

    @AParameter
    @Executor(action = "\\b(钢铁裂缝|钢铁裂隙)\\b")
    fun getHardFissures(context: Context) {
        if (!redisService.hasKey("warframe:hard")) {
            val fissuresJson = HttpUtil.doGetJson(WARFRAME_STATUS_FISSURES, params = mapOf("language" to "zh"))
            val filteredFissures = fissuresJson.filter { eachJson ->
                !eachJson["isStorm"].booleanValue() && eachJson["isHard"].booleanValue()
            }
            getSendFissureList(filteredFissures, "钢铁裂缝")
        }
        val imgData = WebImgUtil.ImgData(
            url = "http://localhost:16666/fissureList?type=hard",
            imgName = "fissureList-${UUID.randomUUID()}",
            element = "body"
        )

        webImgUtil.sendNewImage(context, imgData)
        webImgUtil.deleteImg(imgData = imgData)
    }

    @AParameter
    @Executor(action = "九重天")
    fun getEmpyreanFissures(context: Context) {
        if (!redisService.hasKey("warframe:empyrean")) {
            val fissuresJson = HttpUtil.doGetJson(WARFRAME_STATUS_FISSURES, params = mapOf("language" to "zh"))
            val filteredFissures = fissuresJson.filter { eachJson ->
                eachJson["isStorm"].booleanValue()
            }
            getSendFissureList(filteredFissures, "九重天")
        }
        val imgData = WebImgUtil.ImgData(
            url = "http://localhost:16666/fissureList?type=empyrean",
            imgName = "fissureList-${UUID.randomUUID()}",
            element = "body"
        )

        webImgUtil.sendNewImage(context, imgData)
        webImgUtil.deleteImg(imgData = imgData)
    }

    @AParameter
    @Executor(action = "奸商")
    fun findVoidTrader(context: Context) {
        // 先从Redis中判断商人是否还未到来
        val (expiry, getLocation) = redisService.getExpireAndValue("warframe:voidTraderCome")
        if (expiry != -2L) {
            // 根据缓存过期时间更新提示
            val startString = wfUtil.formatTimeBySecond(expiry!!)
            context.sendMsg("虚空商人仍未回归...\n也许将在 $startString 后抵达 $getLocation")
            return
        }

        // 当Redis中商人到来的缓存不存在时可以判断商人已经回归，尝试通过Redis获取缓存
        // 当Redis中商人的缓存不存在时，尝试通过API获取数据
        if (!redisService.hasKey("warframe:voidTrader")) {
            val traderJson = HttpUtil.doGetJson(WARFRAME_STATUS_VOID_TRADER, params = mapOf("language" to "zh"))

            val startString = traderJson["startString"].asText().replaceTime()
            val endString = traderJson["endString"].asText().replaceTime()
            val location = traderJson["location"].asText().turnZhHans()

            if (traderJson["inventory"].isEmpty) {
                context.sendMsg("虚空商人仍未回归...\n也许将在 $startString 后抵达 $location")
                // 向Redis中写入缓存，用于提示
                redisService.setValueWithExpiry(
                    "warframe:voidTraderCome",
                    location,
                    startString.parseDuration(),
                    TimeUnit.SECONDS
                )
                return
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
                    it.item?.let { it1 -> chinesePattern.matcher(it1).find() }
                })
                val voidTraderEntity = WfStatusVo.VoidTraderEntity(
                    time = endString,
                    location = location,
                    items = sortedItemList
                )

                redisService.setValueWithExpiry(
                    "warframe:voidTrader",
                    voidTraderEntity,
                    endString.parseDuration(),
                    TimeUnit.SECONDS
                )
            }
        }

        // 当Redis中商人的缓存存在时，直接发送图片
        val imgData = WebImgUtil.ImgData(
            url = "http://localhost:16666/voidTrader",
            imgName = "voidTrader-${UUID.randomUUID()}",
            element = "body"
        )

        webImgUtil.sendNewImage(context, imgData)
        webImgUtil.deleteImg(imgData = imgData)
    }

    @AParameter
    @Executor(action = "钢铁")
    fun getSteelPath(context: Context) {
        if (!redisService.hasKey("warframe:steelPath")) {

            val steelPath = HttpUtil.doGetJson(WARFRAME_STATUS_STEEL_PATH, params = mapOf("language" to "zh"))

            val currentReward = steelPath["currentReward"]
            val currentName = currentReward["name"].asText()
            val currentCost = currentReward["cost"].asInt()

            // 寻找下一个奖励
            val rotation = steelPath["rotation"]
            val currentIndex = rotation.indexOfFirst { it["name"].asText() == currentName }
            val nextReward = if (currentIndex != -1 && currentIndex < rotation.size() - 1) {
                rotation[currentIndex + 1]
            } else rotation[0]

            // 获取下一个奖励
            val nextName = nextReward["name"].asText()
            val nextCost = nextReward["cost"].asInt()

            val remaining = steelPath["remaining"].asText().replaceTime()

            val steelPathEntity = WfStatusVo.SteelPathEntity(
                currentName = currentName,
                currentCost = currentCost,
                nextName = nextName,
                nextCost = nextCost,
                remaining = remaining
            )

            redisService.setValueWithExpiry(
                "warframe:steelPath",
                steelPathEntity,
                remaining.parseDuration(),
                TimeUnit.SECONDS
            )
        }

        val imgData = WebImgUtil.ImgData(
            url = "http://localhost:16666/steelPath",
            imgName = "steelPath-${UUID.randomUUID()}",
            element = "body"
        )

        webImgUtil.sendNewImage(context, imgData)
        webImgUtil.deleteImg(imgData = imgData)
    }

    @AParameter
    @Executor(action = "突击")
    fun getSortie(context: Context) {
        if (!redisService.hasKey("warframe:sortie")) {
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

            val sortieEntity = WfStatusVo.SortieEntity(
                faction = faction,
                boss = boss,
                eta = eta,
                taskList = taskList
            )

            redisService.setValueWithExpiry(
                "warframe:sortie",
                sortieEntity,
                eta.parseDuration(),
                TimeUnit.SECONDS
            )
        }
        val imgData = WebImgUtil.ImgData(
            url = "http://localhost:16666/sortie",
            imgName = "sortie-${UUID.randomUUID()}",
            element = "body"
        )

        webImgUtil.sendNewImage(context, imgData)
        webImgUtil.deleteImg(imgData = imgData)
    }

    @AParameter
    @Executor(action = "执(?:行|刑)官")
    fun getArchonHunt(context: Context) {
        if (!redisService.hasKey("warframe:archonHunt")) {
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

            val archonHuntEntity = WfStatusVo.ArchonHuntEntity(
                faction = faction,
                boss = boss,
                eta = eta,
                taskList = taskList,
                rewardItem = rewardItem,
                nextBoss = nextBoss,
                nextRewardItem = nextRewardItem
            )

            redisService.setValueWithExpiry(
                "warframe:archonHunt",
                archonHuntEntity,
                eta.parseDuration(),
                TimeUnit.SECONDS
            )
        }

        val imgData = WebImgUtil.ImgData(
            url = "http://localhost:16666/archonHunt",
            imgName = "archonHuntInfo-${UUID.randomUUID()}",
            element = "body"
        )

        webImgUtil.sendNewImage(context, imgData)
        webImgUtil.deleteImg(imgData = imgData)
    }

    @AParameter
    @Executor(action = "\\b(电波|午夜电波)\\b")
    fun getNightWave(context: Context) {
        if (!redisService.hasKey("warframe:nightWave")) {
            val nightWaveJson = HttpUtil.doGetJson(WARFRAME_STATUS_NIGHT_WAVE, params = mapOf("language" to "zh"))

            val activation = nightWaveJson["activation"].textValue().replace("T", " ").replace(".000Z", "")
            val expiryString = nightWaveJson["expiry"].textValue().replace("T", " ").replace(".000Z", "")

            // 定义时间格式化器
            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
            val nowTime = LocalDateTime.now()
            val tomorrow: LocalDate = nowTime.toLocalDate().plus(1, ChronoUnit.DAYS)  // 明天的日期
            val tomorrowMorning: LocalDateTime = LocalDateTime.of(tomorrow, LocalTime.of(8, 0))  // 明天早上8点


            val timeDifference = wfUtil.formatTimeDifference(nowTime, LocalDateTime.parse(expiryString, formatter))

            // 以当前时间到第二天早上的时间为Redis过期时间
            val expiryTime = ChronoUnit.SECONDS.between(nowTime, tomorrowMorning)
            val nightWaveEntity = WfStatusVo.NightWaveEntity(
                activation = activation,
                startString = nightWaveJson["startString"].textValue().replaceTime().replace("-", ""),
                expiry = expiryString,
                expiryString = timeDifference,
                activeChallenges = nightWaveJson["activeChallenges"].map { item ->
                    WfStatusVo.NightWaveChallenges(
                        title = item["title"].textValue().turnZhHans(),
                        desc = item["desc"].textValue().turnZhHans(),
                        reputation = item["reputation"].intValue(),
                        daily = item["isDaily"].booleanValue()
                    )
                }
            )

            redisService.setValueWithExpiry("warframe:nightWave", nightWaveEntity, expiryTime, TimeUnit.SECONDS)
        }

        val imgData = WebImgUtil.ImgData(
            url = "http://localhost:16666/nightWave",
            imgName = "nightWave-${UUID.randomUUID()}",
            element = "body"
        )

        webImgUtil.sendNewImage(context, imgData)
        webImgUtil.deleteImg(imgData = imgData)
    }

    @AParameter
    @Executor(action = "\\b(火卫二状态|火星状态|火星平原状态|火卫二平原状态|火卫二平原|火星平原)\\b")
    fun phobosStatus(context: Context?): String {
        var wordStatus = redisService.getValue("warframe:phobosStatus", WfStatusVo.WordStatus::class.java)
        if (wordStatus == null) {
            wordStatus = wfUtil.getStatus(WARFRAME_STATUS_PHOBOS_STATUS)
            redisService.setValueWithExpiry(
                "warframe:phobosStatus",
                wordStatus,
                wordStatus.timeLeft!!.parseDuration(),
                TimeUnit.SECONDS
            )
        }

        val sendMsg =
            "当前火卫二平原的状态为: ${wordStatus.displayState} \n开始时间:${wordStatus.activation}\n结束时间:${wordStatus.expiry}\n剩余:${wordStatus.timeLeft}"
        return if (context != null) {
            context.sendMsg(sendMsg)
            sendMsg
        } else sendMsg
    }

    @AParameter
    @Executor(action = "\\b(地球平原状态|希图斯状态|夜灵平原状态|地球平原|夜灵平原)\\b")
    fun cetusCycle(context: Context?): String {
        var wordStatus = redisService.getValue("warframe:cetusCycle", WfStatusVo.WordStatus::class.java)
        if (wordStatus == null) {
            val stateMap = mapOf("night" to "夜晚", "day" to "白天")
            wordStatus = wfUtil.getStatus(WARFRAME_STATUS_CETUS_STATUS, stateMap)
            redisService.setValueWithExpiry(
                "warframe:cetusCycle",
                wordStatus,
                wordStatus.timeLeft!!.parseDuration(),
                TimeUnit.SECONDS
            )
        }
        val sendMsg = "当前希图斯平原的状态为 ${wordStatus.displayState} \n" +
                "开始时间:${wordStatus.activation}\n" +
                "结束时间:${wordStatus.expiry}\n" +
                "剩余:${wordStatus.timeLeft}"

        return if (context != null) {
            context.sendMsg(sendMsg)
            sendMsg
        } else sendMsg
    }

    @AParameter
    @Executor(action = "\\b(地球状态|地球时间|地球)\\b")
    fun earthCycle(context: Context?): String {
        var wordStatus = redisService.getValue("warframe:earthCycle", WfStatusVo.WordStatus::class.java)
        if (wordStatus == null) {
            val stateMap = mapOf("night" to "夜晚", "day" to "白天")
            wordStatus = wfUtil.getStatus(WARFRAME_STATUS_EARTH_STATUS, stateMap)
            redisService.setValueWithExpiry(
                "warframe:earthCycle",
                wordStatus,
                wordStatus.timeLeft!!.parseDuration(),
                TimeUnit.SECONDS
            )
        }
        val sendMsg = "当前地球昼夜的状态为 ${wordStatus.displayState} \n" +
                "开始时间:${wordStatus.activation}\n" +
                "结束时间:${wordStatus.expiry}\n" +
                "剩余:${wordStatus.timeLeft}"

        return if (context != null) {
            context.sendMsg(sendMsg)
            sendMsg
        } else sendMsg
    }

    @AParameter
    @Executor(action = "\\b(金星状态|金星平原状态|福尔图娜状态|福尔图娜平原状态|金星平原|福尔图娜)\\b")
    fun venusStatus(context: Context?): String {
        var wordStatus = redisService.getValue("warframe:venusStatus", WfStatusVo.WordStatus::class.java)
        if (wordStatus == null) {
            val stateMap = mapOf("cold" to "寒冷", "warm" to "温暖")
            wordStatus = wfUtil.getStatus(WARFRAME_STATUS_VENUS_STATUS, stateMap)
            redisService.setValueWithExpiry(
                "warframe:venusStatus",
                wordStatus,
                wordStatus.timeLeft!!.parseDuration(),
                TimeUnit.SECONDS
            )
        }
        val sendMsg = "当前金星平原的状态为 ${wordStatus.displayState} \n" +
                "开始时间:${wordStatus.activation}\n" +
                "结束时间:${wordStatus.expiry}\n" +
                "剩余:${wordStatus.timeLeft}"
        return if (context != null) {
            context.sendMsg(sendMsg)
            sendMsg
        } else sendMsg
    }

    @AParameter
    @Executor(action = "\\b(平原|全部平原|平原时间)\\b")
    fun allPlain(context: Context) {
        val phobosDeferred = phobosStatus(null)
        val cetusDeferred = cetusCycle(null)
        val earthDeferred = earthCycle(null)
        val venusDeferred = venusStatus(null)

        // 整合所有状态信息
        val allStatus = "$phobosDeferred\n\n" +
                "\n$cetusDeferred\n\n" +
                "\n$earthDeferred\n\n" +
                "\n$venusDeferred"

        // 发送消息
        context.sendMsg(allStatus)
    }

    @AParameter
    @Executor(action = "\\b入侵\\b")
    fun invasions(context: Context) {
        if (!redisService.hasKey("warframe:invasions")) {
            val invasionsArray = HttpUtil.doGetJson(WARFRAME_STATUS_INVASIONS, params = mapOf("language" to "zh"))
            val invasionsList = mutableListOf<WfStatusVo.InvasionsEntity>()
            invasionsArray.forEach { invasionsJson ->
                if (!invasionsJson["completed"].booleanValue()) {
                    val entity = WfStatusVo.InvasionsEntity(
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
                    invasionsList.add(entity)
                }
            }
            redisService.setValueWithExpiry("warframe:invasions", invasionsList, 3L, TimeUnit.MINUTES)
        }
        val imgData = WebImgUtil.ImgData(
            url = "http://localhost:16666/invasions",
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
        var incarnon = redisService.getValue("warframe:incarnon", WfStatusVo.IncarnonEntity::class.java)

        if (incarnon == null) {
            val mapper = jacksonObjectMapper()
            // 使用redis缓存后只需要每周从原始数据更新一次数据至当前时间即可
            // 原代码也同样需要更新一次数据，所以可以省略
            val jsonFile = File(WARFRAME_INCARNON)

            // 读取并解析 JSON 文件
            val data: WfUtil.Data = mapper.readValue(jsonFile, WfUtil.Data::class.java)

            // 当前日期
            val currentTime = LocalDateTime.now(ZoneId.of("Asia/Shanghai"))

            val oneWeekLater = currentTime.plusWeeks(1)

            // 更新 week 的 startTime
            val updatedOrdinaryWeeks = wfUtil.updateWeeks(data, currentTime)

            // 查找当前周的数据
            val currentOrdinaryWeek = wfUtil.findCurrentWeek(updatedOrdinaryWeeks.ordinary, currentTime)
            val currentSteelWeek = wfUtil.findCurrentWeek(updatedOrdinaryWeeks.steel, currentTime)

            // 更新 下周 的 startTime
            val updatedNextWeeks = wfUtil.updateWeeks(data, oneWeekLater)

            // 查找下周的数据
            val nextOrdinaryWeek = wfUtil.findCurrentWeek(updatedNextWeeks.ordinary, oneWeekLater)
            val nextSteelWeek = wfUtil.findCurrentWeek(updatedNextWeeks.steel, oneWeekLater)

            if (currentOrdinaryWeek == null || currentSteelWeek == null || nextOrdinaryWeek == null || nextSteelWeek == null) {
                context.sendMsg(WarframeRespEnum.INCARNON_ERROR.message)
                return
            }

            // 获取下周一的日期
            val nextMonday = currentTime.with(TemporalAdjusters.next(DayOfWeek.MONDAY))

            // 设置时间为下周一早上8点
            val nextMondayAt8 = nextMonday.withHour(8).withMinute(0).withSecond(0).withNano(0)

            val remainTime = wfUtil.formatTimeDifference(currentTime, nextMondayAt8)
            val incarnonEntity = WfStatusVo.IncarnonEntity(
                thisWeekData = WfUtil.Data(
                    ordinary = listOf(currentOrdinaryWeek),
                    steel = listOf(currentSteelWeek)
                ),
                nextWeekData = WfUtil.Data(
                    ordinary = listOf(nextOrdinaryWeek),
                    steel = listOf(nextSteelWeek)
                ),
                remainTime = remainTime
            )

            redisService.setValueWithExpiry(
                "warframe:incarnon",
                incarnonEntity,
                incarnonEntity.remainTime!!.parseDuration(),
                TimeUnit.SECONDS
            )

            incarnon = incarnonEntity
        }

        if (!redisService.hasKey("warframe:incarnonRiven")) {
            // 根据 灵化武器紫卡价格 给出每周推荐武器
            var incarnonRiven = wfUtil.getIncarnonRiven(incarnon.thisWeekData?.steel)
            if (incarnonRiven == null) incarnonRiven = mapOf()
            redisService.setValueWithExpiry(
                "warframe:incarnonRiven",
                incarnonRiven,
                30L,
                TimeUnit.MINUTES
            )
        }

        val imgData = WebImgUtil.ImgData(
            url = "http://localhost:16666/incarono",
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
        if (!redisService.hasKey("warframe:moodSpirals")) {
            val jsonFile = File(WARFRAME_MOOD_SPIRALS)
            val mapper = jacksonObjectMapper()
            var weatherData: WfUtilVo.SpiralsData = mapper.readValue(jsonFile, WfUtilVo.SpiralsData::class.java)
            val currentTime = LocalDateTime.now(ZoneId.of("Asia/Shanghai"))

            weatherData = wfUtil.updateWeathers(weatherData, currentTime)
            val currentWeatherData = wfUtil.findSpiralsCurrentTime(weatherData.wfWeather, currentTime)

            if (currentWeatherData == null) {
                context.sendMsg(WarframeRespEnum.SPIRALS_ERROR.message)
                return
            }

            val hoursLater = OffsetDateTime.parse(currentWeatherData.startTime, dateTimeFormatter)
                .toLocalDateTime()
                .plusHours(2)
                .plusMinutes(1)

            weatherData = wfUtil.updateWeathers(weatherData, hoursLater)
            val hoursLaterWeatherData = wfUtil.findSpiralsCurrentTime(weatherData.wfWeather, hoursLater)

            if (hoursLaterWeatherData == null) {
                context.sendMsg(WarframeRespEnum.SPIRALS_ERROR.message)
                return
            }

            // 获取当前和下一个天气的状态
            val currentWeatherState = weatherData.weatherStates[currentWeatherData.stateId]
            val damageType = weatherData.damageTypes[currentWeatherData.dmgStateId]
            val nextWeatherState = weatherData.weatherStates[hoursLaterWeatherData.stateId]

            // 确保状态不为null
            if (currentWeatherState == null || damageType == null || nextWeatherState == null) {
                context.sendMsg(WarframeRespEnum.SPIRALS_ABNORMAL_ERROR.message)
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
            val moodSpiralsEntity = WfStatusVo.MoodSpiralsEntity(
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

            redisService.setValueWithExpiry(
                "warframe:moodSpirals",
                moodSpiralsEntity,
                moodSpiralsEntity.remainTime!!.replace(" ", "").parseDuration(),
                TimeUnit.SECONDS
            )
        }
        // 生成和发送图像
        val imgData = WebImgUtil.ImgData(
            url = "http://localhost:16666/spirals",
            imgName = "spirals-${UUID.randomUUID()}",
            element = "body"
        )

        webImgUtil.sendNewImage(context, imgData)
        webImgUtil.deleteImg(imgData = imgData)
        System.gc()
    }
}