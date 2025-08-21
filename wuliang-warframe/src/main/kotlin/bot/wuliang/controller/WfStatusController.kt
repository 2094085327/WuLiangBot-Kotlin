package bot.wuliang.controller

import bot.wuliang.botLog.logAop.SystemLog
import bot.wuliang.botUtil.BotUtils
import bot.wuliang.config.*
import bot.wuliang.config.WfMarketConfig.WF_ARCHONHUNT_KEY
import bot.wuliang.config.WfMarketConfig.WF_CETUS_CYCLE_KEY
import bot.wuliang.config.WfMarketConfig.WF_EARTH_CYCLE_KEY
import bot.wuliang.config.WfMarketConfig.WF_FISSURE_KEY
import bot.wuliang.config.WfMarketConfig.WF_INCARNON_KEY
import bot.wuliang.config.WfMarketConfig.WF_INVASIONS_KEY
import bot.wuliang.config.WfMarketConfig.WF_MOODSPIRALS_KEY
import bot.wuliang.config.WfMarketConfig.WF_NIGHTWAVE_KEY
import bot.wuliang.config.WfMarketConfig.WF_PHOBOS_STATUS_KEY
import bot.wuliang.config.WfMarketConfig.WF_SORTIE_KEY
import bot.wuliang.config.WfMarketConfig.WF_VENUS_STATUS_KEY
import bot.wuliang.config.WfMarketConfig.WF_VOIDTRADER_KEY
import bot.wuliang.distribute.annotation.AParameter
import bot.wuliang.distribute.annotation.ActionService
import bot.wuliang.distribute.annotation.Executor
import bot.wuliang.entity.vo.WfStatusVo
import bot.wuliang.entity.vo.WfUtilVo
import bot.wuliang.httpUtil.HttpUtil
import bot.wuliang.httpUtil.ProxyUtil
import bot.wuliang.imageProcess.WebImgUtil
import bot.wuliang.moudles.VoidTrader
import bot.wuliang.otherUtil.OtherUtil.STConversion.turnZhHans
import bot.wuliang.redis.RedisService
import bot.wuliang.respEnum.WarframeRespEnum
import bot.wuliang.utils.ParseDataUtil
import bot.wuliang.utils.TimeUtils
import bot.wuliang.utils.TimeUtils.formatDuration
import bot.wuliang.utils.TimeUtils.formatTimeDifference
import bot.wuliang.utils.TimeUtils.getNextRefreshTime
import bot.wuliang.utils.TimeUtils.parseDuration
import bot.wuliang.utils.WfStatus.replaceFaction
import bot.wuliang.utils.WfUtil
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlinx.coroutines.runBlocking
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.io.File
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.regex.Matcher


/**
 * @description: Warframe 世界状态
 * @author Nature Zero
 * @date 2024/6/9 上午12:35
 */
@Suppress("HttpUrlsUsage")
@Component
@ActionService
class WfStatusController @Autowired constructor(
    private val webImgUtil: WebImgUtil,
    private val wfUtil: WfUtil,
    private val redisService: RedisService,
    private val proxyUtil: ProxyUtil,
    private val parseDataUtil: ParseDataUtil
) {
    private val dateTimeFormatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME

    @SystemLog(businessName = "获取裂缝信息")
    @AParameter
    @Executor(action = "\\b(裂缝|裂隙|钢铁裂缝|钢铁裂隙|九重天)\\b")
    fun getFissures(context: BotUtils.Context, matcher: Matcher) {
        val fissureType = matcher.group(1)
        // 检查 Redis 缓存是否存在，若不存在则从网络获取数据
        if (!redisService.hasKey(WF_FISSURE_KEY)) {
            val data = HttpUtil.doGetJson(WARFRAME_STATUS_URL)
            runBlocking {
                parseDataUtil.parseFissure(data["ActiveMissions"], data["VoidStorms"])
            }
        }

        // 根据不同的裂缝类型构造图片的 URL
        val urlSuffix = when (fissureType) {
            "裂缝","裂隙" -> "ordinary"
            "钢铁裂缝","钢铁裂隙" -> "hard"
            "九重天" -> "storm"
            else -> "ordinary"
        }

        val imgData = WebImgUtil.ImgData(
            url = "http://${webImgUtil.frontendAddress}/fissureList?type=$urlSuffix",
            imgName = "fissureList-${UUID.randomUUID()}",
            element = "#app"
        )

        webImgUtil.sendNewImage(context, imgData)
        webImgUtil.deleteImg(imgData = imgData)
    }


    @SystemLog(businessName = "获取奸商信息")
    @AParameter
    @Executor(action = "奸商")
    fun findVoidTrader(context: BotUtils.Context) {
        if (!redisService.hasKey(WF_VOIDTRADER_KEY)) {
            val data = HttpUtil.doGetJson(WARFRAME_STATUS_URL)
            parseDataUtil.parseVoidTraders(data["VoidTraders"])
        }

        val voidTraderList = redisService.getValueTyped<List<VoidTrader>>(WF_VOIDTRADER_KEY)
        if (voidTraderList.isNullOrEmpty()) {
            context.sendMsg("糟糕OωO，虚空商人不见了，请联系管理员进行检查")
            return
         }

        val activeVoidList = voidTraderList.filter { it.isActive==true }
        if (activeVoidList.isEmpty()) {
            val arrivalTime = voidTraderList.first().expiry?.minus(Duration.ofDays(2))
            val etaTime = formatDuration(Duration.between(TimeUtils.getInstantNow(), arrivalTime))
            context.sendMsg("虚空商人仍未回归...\n也许将在 $etaTime 后抵达 ${voidTraderList.first().node}")
            return
        }

        // 当Redis中商人的缓存存在时，直接发送图片
        val imgData = WebImgUtil.ImgData(
            url = "http://${webImgUtil.frontendAddress}/voidTrader",
            imgName = "voidTrader-${UUID.randomUUID()}",
            element = "#app"
        )

        webImgUtil.sendNewImage(context, imgData)
    }

    @SystemLog(businessName = "获取钢铁之路兑换信息")
    @AParameter
    @Executor(action = "钢铁")
    fun getSteelPath(context: BotUtils.Context) {
        val imgData = WebImgUtil.ImgData(
            url = "http://${webImgUtil.frontendAddress}/steelPath",
            imgName = "steelPath-${UUID.randomUUID()}",
            element = "#app"
        )

        webImgUtil.sendNewImage(context, imgData)
        webImgUtil.deleteImg(imgData = imgData)
    }

    @SystemLog(businessName = "获取日突击信息")
    @AParameter
    @Executor(action = "突击")
    fun getSortie(context: BotUtils.Context) {
        if (!redisService.hasKey(WF_SORTIE_KEY)) {
            val data = HttpUtil.doGetJson(WARFRAME_STATUS_URL)
            parseDataUtil.parseSorties(data["Sorties"])
        }
        val imgData = WebImgUtil.ImgData(
            url = "http://${webImgUtil.frontendAddress}/sortie",
            imgName = "sortie-${UUID.randomUUID()}",
            element = "#app"
        )

        webImgUtil.sendNewImage(context, imgData)
        webImgUtil.deleteImg(imgData = imgData)
    }

    @SystemLog(businessName = "获取执刑官信息")
    @AParameter
    @Executor(action = "执(?:行|刑)官")
    fun getArchonHunt(context: BotUtils.Context) {
        if (!redisService.hasKey(WF_ARCHONHUNT_KEY)) {
            val data = HttpUtil.doGetJson(WARFRAME_STATUS_URL)
            parseDataUtil.parseArchonHunt(data["LiteSorties"])
        }

        val imgData = WebImgUtil.ImgData(
            url = "http://${webImgUtil.frontendAddress}/archonHunt",
            imgName = "archonHuntInfo-${UUID.randomUUID()}",
            element = "#app"
        )

        webImgUtil.sendNewImage(context, imgData)
        webImgUtil.deleteImg(imgData = imgData)
    }

    @SystemLog(businessName = "获取午夜电波信息")
    @AParameter
    @Executor(action = "\\b(电波|午夜电波)\\b")
    fun getNightWave(context: BotUtils.Context) {
        if (!redisService.hasKey(WF_NIGHTWAVE_KEY)) {
            val data = HttpUtil.doGetJson(WARFRAME_STATUS_URL)
            parseDataUtil.parseNightWave(data["SeasonInfo"])
        }

        val imgData = WebImgUtil.ImgData(
            url = "http://${webImgUtil.frontendAddress}/nightWave",
            imgName = "nightWave-${UUID.randomUUID()}",
            element = "#app"
        )

        webImgUtil.sendNewImage(context, imgData)
        webImgUtil.deleteImg(imgData = imgData)
    }

    @SystemLog(businessName = "获取火卫二循环信息")
    @AParameter
    @Executor(action = "\\b(火卫二状态|火星状态|火星平原状态|火卫二平原状态|火卫二平原|火星平原)\\b")
    fun phobosStatus(context: BotUtils.Context?): String {
        var wordStatus = redisService.getValueTyped<WfStatusVo.WordStatus>(WF_PHOBOS_STATUS_KEY)
        if (wordStatus == null) {
            wordStatus = wfUtil.getStatus(WARFRAME_STATUS_PHOBOS_STATUS)
            redisService.setValueWithExpiry(
                WF_PHOBOS_STATUS_KEY,
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

    @SystemLog(businessName = "获取夜灵平原昼夜循环信息")
    @AParameter
    @Executor(action = "\\b(地球平原状态|希图斯状态|夜灵平原状态|地球平原|夜灵平原)\\b")
    fun cetusCycle(context: BotUtils.Context?): String {
        var wordStatus = redisService.getValueTyped<WfStatusVo.WordStatus>(WF_CETUS_CYCLE_KEY)
        if (wordStatus == null) {
            val stateMap = mapOf("night" to "夜晚", "day" to "白天")
            wordStatus = wfUtil.getStatus(WARFRAME_STATUS_CETUS_STATUS, stateMap)
            redisService.setValueWithExpiry(
                WF_CETUS_CYCLE_KEY,
                wordStatus,
                wordStatus.timeLeft!!.parseDuration().coerceAtLeast(1),// 似乎在一定条件下剩余时间会变为负数产生报错
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

    @SystemLog(businessName = "获取地球昼夜循环信息")
    @AParameter
    @Executor(action = "\\b(地球状态|地球时间|地球)\\b")
    fun earthCycle(context: BotUtils.Context?): String {
        var wordStatus = redisService.getValueTyped<WfStatusVo.WordStatus>(WF_EARTH_CYCLE_KEY)
        if (wordStatus == null) {
            val stateMap = mapOf("night" to "夜晚", "day" to "白天")
            wordStatus = wfUtil.getStatus(WARFRAME_STATUS_EARTH_STATUS, stateMap)
            redisService.setValueWithExpiry(
                WF_EARTH_CYCLE_KEY,
                wordStatus,
                wordStatus.timeLeft!!.parseDuration().coerceAtLeast(1),
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

    @SystemLog(businessName = "获取金星冷热循环信息")
    @AParameter
    @Executor(action = "\\b(金星状态|金星平原状态|福尔图娜状态|福尔图娜平原状态|金星平原|福尔图娜)\\b")
    fun venusStatus(context: BotUtils.Context?): String {
        var wordStatus = redisService.getValueTyped<WfStatusVo.WordStatus>(WF_VENUS_STATUS_KEY)
        if (wordStatus == null) {
            val stateMap = mapOf("cold" to "寒冷", "warm" to "温暖")
            wordStatus = wfUtil.getStatus(WARFRAME_STATUS_VENUS_STATUS, stateMap)
            redisService.setValueWithExpiry(
                WF_VENUS_STATUS_KEY,
                wordStatus,
                wordStatus.timeLeft!!.parseDuration().coerceAtLeast(1),
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

    @SystemLog(businessName = "获取全部平原循环信息")
    @AParameter
    @Executor(action = "\\b(平原|全部平原|平原时间)\\b")
    fun allPlain(context: BotUtils.Context) {
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

    @SystemLog(businessName = "获取入侵列表")
    @AParameter
    @Executor(action = "\\b入侵\\b")
    fun invasions(context: BotUtils.Context) {
        if (!redisService.hasKey(WF_INVASIONS_KEY)) {
            val invasionsArray = HttpUtil.doGetJson(
                WARFRAME_STATUS_INVASIONS,
                params = mapOf("language" to "zh"),
                proxy = proxyUtil.randomProxy()
            )
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
            redisService.setValueWithExpiry(WF_INVASIONS_KEY, invasionsList, 3L, TimeUnit.MINUTES)
        }
        val imgData = WebImgUtil.ImgData(
            url = "http://${webImgUtil.frontendAddress}/invasions",
            imgName = "invasions-${UUID.randomUUID()}",
            element = "#app"
        )

        webImgUtil.sendNewImage(context, imgData)
        webImgUtil.deleteImg(imgData = imgData)
        System.gc()
    }

    @SystemLog(businessName = "获取本周灵化信息")
    @AParameter
    @Executor(action = "\\b(本周灵化|这周灵化|灵化|回廊|钢铁回廊|本周回廊)\\b")
    fun incarnon(context: BotUtils.Context) {
        var incarnon = redisService.getValueTyped<WfStatusVo.IncarnonEntity>(WF_INCARNON_KEY)

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

            val remainTime = formatTimeDifference(currentTime, nextMondayAt8)
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
                WF_INCARNON_KEY,
                incarnonEntity,
                incarnonEntity.remainTime!!.parseDuration(),
                TimeUnit.SECONDS
            )

            incarnon = incarnonEntity
        }

        // TODO 暂时移除每周武器推荐，其基于频繁查询WM紫卡价格，导致被WM禁止访问，找到解决方法后再优化
//        if (!redisService.hasKey(WF_INCARNON_RIVEN_KEY)) {
//            // 根据 灵化武器紫卡价格 给出每周推荐武器
//            var incarnonRiven = wfUtil.getIncarnonRiven(incarnon.thisWeekData?.steel)
//            if (incarnonRiven == null) incarnonRiven = mapOf()
//            redisService.setValueWithExpiry(
//                WF_INCARNON_RIVEN_KEY,
//                incarnonRiven,
//                30L,
//                TimeUnit.MINUTES
//            )
//        }

        val imgData = WebImgUtil.ImgData(
            url = "http://${webImgUtil.frontendAddress}/incarono",
            imgName = "incarnon-${UUID.randomUUID()}",
            element = "#app"
        )

        webImgUtil.sendNewImage(context, imgData)
        webImgUtil.deleteImg(imgData = imgData)
        System.gc()
    }

    @SystemLog(businessName = "获取双衍平原状态信息")
    @AParameter
    @Executor(action = "\\b(双衍|双衍平原|双衍状态|双衍平原状态|回廊状态|虚空平原状态|复眠螺旋|复眠螺旋状态|王境状态)\\b")
    fun moodSpirals(context: BotUtils.Context) {
        if (!redisService.hasKey(WF_MOODSPIRALS_KEY)) {
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
            val timeUntilNextWeatherFormatted = formatDuration(timeUntilNextWeather)

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
                WF_MOODSPIRALS_KEY,
                moodSpiralsEntity,
                moodSpiralsEntity.remainTime!!.replace(" ", "").parseDuration(),
                TimeUnit.SECONDS
            )
        }
        // 生成和发送图像
        val imgData = WebImgUtil.ImgData(
            url = "http://${webImgUtil.frontendAddress}/spirals",
            imgName = "spirals-${UUID.randomUUID()}",
            element = "#app"
        )

        webImgUtil.sendNewImage(context, imgData)
        webImgUtil.deleteImg(imgData = imgData)
        System.gc()
    }

    @SystemLog(businessName = "获取信条/终幕武器轮换信息")
    @AParameter
    @Executor(action = "\\b(佩兰|信条|终幕|信条轮换|终幕轮换)\\b")
    fun weaponRotation(context: BotUtils.Context) {
        // 信条刷新日期 2025-03-29 08:00:00
        val tenetStartDate = LocalDateTime.of(2025, 3, 29, 8, 0, 0)
        // 终幕刷新日期 2025-03-19 08:00:00
        val codaStartDate = LocalDateTime.of(2025, 3, 19, 8, 0, 0)

        // 每4天刷新一次
        val refreshInterval = Duration.ofDays(4)

        val now = LocalDateTime.now()
        // 每4天刷新一次，计算剩余刷新时间
        val tenetNextRefresh = getNextRefreshTime(tenetStartDate, now, refreshInterval)
        // 计算下一次终幕刷新时间
        val codaNextRefresh = getNextRefreshTime(codaStartDate, now, refreshInterval)

        // 计算剩余时间
        val tenetRemainingTime = Duration.between(now, tenetNextRefresh)
        val codaRemainingTime = Duration.between(now, codaNextRefresh)

        // 格式化剩余时间
        val formattedTenetRemainingTime = formatDuration(tenetRemainingTime)
        val formattedCodaRemainingTime = formatDuration(codaRemainingTime)

        // 定义日期时间格式化器
        val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

        // 格式化下一次刷新时间
        val formattedTenetNextRefresh = tenetNextRefresh.format(dateTimeFormatter)
        val formattedCodaNextRefresh = codaNextRefresh.format(dateTimeFormatter)

        // 发送消息
        context.sendMsg("信条下一次刷新时间: $formattedTenetNextRefresh  剩余时间: $formattedTenetRemainingTime\n终幕下一次刷新时间: $formattedCodaNextRefresh  剩余时间: $formattedCodaRemainingTime")
    }
}