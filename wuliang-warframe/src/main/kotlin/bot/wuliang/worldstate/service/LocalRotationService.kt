package bot.wuliang.worldstate.service

import bot.wuliang.config.WARFRAME_INCARNON
import bot.wuliang.config.WfMarketConfig.WF_INCARNON_KEY
import bot.wuliang.config.WfMarketConfig.WF_RIVEN_UN_REROLLED_KEY
import bot.wuliang.config.WfMarketConfig.WF_SIMARIS_KEY
import bot.wuliang.config.WfMarketConfig.WF_STEELPATH_KEY
import bot.wuliang.jacksonUtil.JacksonUtil
import bot.wuliang.moudles.*
import bot.wuliang.redis.RedisService
import bot.wuliang.riven.RivenRankingService
import bot.wuliang.translation.PublicExportService
import bot.wuliang.translation.WorldStateCatalog
import bot.wuliang.utils.TimeUtils.formatDuration
import bot.wuliang.utils.TimeUtils.getFirstDayOfWeek
import bot.wuliang.utils.TimeUtils.getInstantNow
import bot.wuliang.utils.TimeUtils.getLastDayOfWeek
import bot.wuliang.utils.TimeUtils.getNextMonday
import bot.wuliang.utils.TimeUtils.getStartOfDay
import bot.wuliang.utils.TimeUtils.getTimeOfNextDay
import bot.wuliang.utils.TimeUtils.parseDuration
import com.fasterxml.jackson.databind.JsonNode
import org.springframework.stereotype.Service
import java.io.File
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

/** 根据本地配置提供钢路、结合仪式和回廊补充数据；下一轮预测不替代官方当前任务。 */
@Service
class LocalRotationService(
    private val redisService: RedisService,
    private val translations: PublicExportService,
    private val worldStateCatalog: WorldStateCatalog,
    private val rivenRankingService: RivenRankingService,
) {
    private val incarnonWeekSeconds = 604800L

    /** 返回缓存剩余秒数与钢路奖励，沿用原有页面的响应和周轮换锚点。 */
    fun getSteelPath(): Pair<Long?, SteelPath?> {
        translations.index()
        val cacheKey = translations.cacheKey(WF_STEELPATH_KEY)
        if (redisService.hasKey(cacheKey)) return redisService.getExpireAndValueTyped<SteelPath>(
            cacheKey
        )
        val rotation = worldStateCatalog.steelRewards
        val start = Instant.parse("2020-11-16T00:00:00.000Z")
        val sSinceStart = Duration.between(start, Instant.now()).seconds
        val eightWeeks = 4838400
        val sevenDays = 604800

        val ind = ((sSinceStart % eightWeeks) / sevenDays).toInt()

        // 计算下周的索引
        val nextInd = (ind + 1) % 8 // 共有8个周期

        val activation = getFirstDayOfWeek()
        val expiry = getLastDayOfWeek()

        val steelPathEntity = SteelPath(
            id = "spi:${getStartOfDay().toEpochMilli()}",
            activation = activation,
            expiry = expiry,
            eta = formatDuration(Duration.between(getInstantNow(), expiry)),
            currentItem = worldStateCatalog.steelName(rotation[ind]),
            currentCost = rotation[ind].cost,
            nextItem = worldStateCatalog.steelName(rotation[nextInd]),
            nextCost = rotation[nextInd].cost
        )
        redisService.setValueWithExpiry(
            cacheKey,
            steelPathEntity,
            steelPathEntity.eta?.parseDuration() ?: 0L,
            TimeUnit.SECONDS
        )
        return Pair(steelPathEntity.eta?.parseDuration(), steelPathEntity)
    }

    /** 官方目标标识结合本地地点配置生成响应，缓存至下一个日更边界。 */
    fun getSimaris(simarisJson: JsonNode): Simaris? {
        translations.index()
        val cacheKey = translations.cacheKey(WF_SIMARIS_KEY)
        if (redisService.hasKey(cacheKey)) return redisService.getValueTyped<Simaris>(cacheKey)
        val targetItem = simarisJson["LastCompletedTargetType"].textValue()
        val simarisPersistent = worldStateCatalog.synthesisTarget(targetItem)
            ?: SimarisPersistent(imageKey = null, name = translations.name(targetItem), locations = emptyList())
        val today = getStartOfDay()
        val nextDay = getTimeOfNextDay(today)
        val simaris = Simaris(
            imageKey = simarisPersistent.imageKey,
            name = simarisPersistent.name,
            activation = today,
            expiry = nextDay,
            eta = formatDuration(Duration.between(getInstantNow(), nextDay)),
            locations = simarisPersistent.locations,
        )
        val expire = simaris.eta?.parseDuration() ?: 300
        redisService.setValueWithExpiry(cacheKey, simaris, expire, TimeUnit.SECONDS)
        return simaris
    }

    /** 本地回廊配置提供本周、下周及紫卡价格补充，不替代面板的官方当前回廊。 */
    fun getIncarnon(): Incarnon? {
        translations.index()
        val cacheKey = translations.cacheKey(WF_INCARNON_KEY)
        if (redisService.hasKey(cacheKey)) return redisService.getValueTyped<Incarnon>(cacheKey)
        val expire = Duration.between(Instant.now(), getNextMonday()).seconds

        val incarnonJson = JacksonUtil.readTree(File(WARFRAME_INCARNON))
        val ordinaryJson = incarnonJson["ordinary"]
        val steelJson = incarnonJson["steel"]
        val ordinaryScheduleJson = incarnonJson["ordinarySchedule"]
        val steelScheduleJson = incarnonJson["steelSchedule"]

        // 获取到现在经过的秒数
        val now = Instant.now()
        val ordinaryInd = calculateIncarnonIndex(ordinaryScheduleJson, ordinaryJson.size(), now)
        val steelInd = calculateIncarnonIndex(steelScheduleJson, steelJson.size(), now)

        // 计算下周的索引
        val activation = getFirstDayOfWeek()
        val expiry = getNextMonday()
        val ordinaryNextInd = calculateIncarnonIndex(ordinaryScheduleJson, ordinaryJson.size(), expiry)
        val steelNextInd = calculateIncarnonIndex(steelScheduleJson, steelJson.size(), expiry)

        // 确保执行灵化解析时已存在紫卡价格数据
        rivenRankingService.refreshRanking()
        val rivenPriceList = redisService.getValueTyped<List<Riven>>(translations.cacheKey(WF_RIVEN_UN_REROLLED_KEY))

        /** 将钢铁回廊候选转换为名称和紫卡价格，缺少价格时使用零值。 */
        fun processSteelItems(steelJsonNode: JsonNode): List<Incarnon.SteelItem> {
            return steelJsonNode["items"].map { item ->
                val urlName = item["url_name"].textValue()
                val adapter = translations.index().incarnonAdapter(urlName)
                Incarnon.SteelItem(
                    name = translations.name(adapter ?: urlName),
                    urlName = urlName,
                    rivenPrice = rivenPriceList?.find {
                        it.compatibilityId.equals(urlName.replace('_', ' '), ignoreCase = true)
                    }?.median ?: 0.0,
                )
            }.toList()
        }

        val incarnon = Incarnon(
            thisWeek = Incarnon.IncarnonData(
                ordinary = Incarnon.WeekData(
                    week = ordinaryInd + 1,
                    items = ordinaryJson[ordinaryInd]["items"].map { translations.name(it.textValue()) },
                ),
                steel = Incarnon.WeekData(
                    week = steelInd + 1,
                    items = processSteelItems(steelJson[steelInd]),
                )
            ),
            nextWeek = Incarnon.IncarnonData(
                ordinary = Incarnon.WeekData(
                    week = ordinaryNextInd + 1,
                    items = ordinaryJson[ordinaryNextInd]["items"].map { translations.name(it.textValue()) },
                ),
                steel = Incarnon.WeekData(
                    week = steelNextInd + 1,
                    items = processSteelItems(steelJson[steelNextInd])
                )
            ),
            activation = activation,
            expiry = expiry,
            eta = formatDuration(Duration.between(getInstantNow(), expiry))
        )

        redisService.setValueWithExpiry(cacheKey, incarnon, expire, TimeUnit.SECONDS)
        return incarnon
    }

    /**
     * 选择最近生效的本地排期，按完整周数计算循环索引。
     *
     * @param scheduleJson 本地轮换排期数组。
     * @param itemSize 可用候选数量，用于校验循环范围。
     * @param now 需要计算轮换的查询时刻。
     * @return 当前轮换在候选数组中的零基索引。
     * @throws IllegalArgumentException 循环长度或起始索引超出允许范围时抛出。
     */
    private fun calculateIncarnonIndex(scheduleJson: JsonNode, itemSize: Int, now: Instant): Int {
        val schedule = scheduleJson
            .filter { !Instant.parse(it["startTime"].textValue()).isAfter(now) }
            .maxByOrNull { Instant.parse(it["startTime"].textValue()) }
            ?: scheduleJson.first()

        val startTime = Instant.parse(schedule["startTime"].textValue())
        val startIndex = schedule["startIndex"].longValue()
        val cycleSize = schedule["size"].longValue()
        require(cycleSize in 1..itemSize.toLong()) { "无效的灵化索引大小: $cycleSize" }
        require(startIndex in 0 until cycleSize) { "无效的灵化开始索引: $startIndex" }

        val elapsedWeeks = Math.floorDiv(Duration.between(startTime, now).seconds, incarnonWeekSeconds)
        return Math.floorMod(startIndex + elapsedWeeks, cycleSize).toInt()
    }
}