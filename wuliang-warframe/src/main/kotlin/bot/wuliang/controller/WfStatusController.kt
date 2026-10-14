package bot.wuliang.controller

import bot.wuliang.adapter.context.ExecutionContext
import bot.wuliang.config.WfMarketConfig.WF_ARCHONHUNT_KEY
import bot.wuliang.config.WfMarketConfig.WF_CALENDAR_KEY
import bot.wuliang.config.WfMarketConfig.WF_CONQUEST_KEY
import bot.wuliang.config.WfMarketConfig.WF_INCARNON_KEY
import bot.wuliang.config.WfMarketConfig.WF_INVASIONS_KEY
import bot.wuliang.config.WfMarketConfig.WF_NIGHTWAVE_KEY
import bot.wuliang.config.WfMarketConfig.WF_RIVEN_REROLLED_KEY
import bot.wuliang.config.WfMarketConfig.WF_RIVEN_UN_REROLLED_KEY
import bot.wuliang.config.WfMarketConfig.WF_SIMARIS_KEY
import bot.wuliang.config.WfMarketConfig.WF_SORTIE_KEY
import bot.wuliang.config.WfMarketConfig.WF_VOIDTRADER_KEY
import bot.wuliang.distribute.annotation.AParameter
import bot.wuliang.distribute.annotation.ActionService
import bot.wuliang.distribute.annotation.Executor
import bot.wuliang.imageProcess.WebImgUtil
import bot.wuliang.logAop.SystemLog
import bot.wuliang.moudles.VoidTrader
import bot.wuliang.redis.RedisService
import bot.wuliang.respEnum.WarframeRespEnum
import bot.wuliang.service.WarframeDataService
import bot.wuliang.translation.PublicExportService
import bot.wuliang.utils.ParseDataUtil
import bot.wuliang.utils.TimeUtils
import bot.wuliang.utils.TimeUtils.formatDuration
import bot.wuliang.utils.WfUtil
import bot.wuliang.worldstate.WorldStateSource
import bot.wuliang.worldstate.WorldStateUnavailableException
import bot.wuliang.worldstate.WikiWeaponRotationSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.*
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
    private val worldStateSource: WorldStateSource,
    private val translations: PublicExportService,
    private val webImgUtil: WebImgUtil,
    private val wfUtil: WfUtil,
    private val redisService: RedisService,
    private val parseDataUtil: ParseDataUtil,
    private val warframeDataService: WarframeDataService,
    private val wikiWeaponRotations: WikiWeaponRotationSource,
) {

    @SystemLog(businessName = "获取裂缝信息")
    @AParameter
    @Executor(action = "\\b(裂缝|裂隙|钢铁裂缝|钢铁裂隙|九重天)\\b")
    suspend fun getFissures(context: ExecutionContext, matcher: Matcher) {
        if (!translations.ready) {
            context.sender.sendText(PublicExportService.UNAVAILABLE_MESSAGE)
            return
        }
        val fissureType = matcher.group(1)
        // 根据不同的裂缝类型构造图片的 URL
        val urlSuffix = when (fissureType) {
            "裂缝", "裂隙" -> "ordinary"
            "钢铁裂缝", "钢铁裂隙" -> "hard"
            "九重天" -> "storm"
            else -> "ordinary"
        }

        val imgData = WebImgUtil.ImgData(
            url = "http://${webImgUtil.frontendAddress}/fissureList?type=$urlSuffix",
            imgName = "fissureList-${UUID.randomUUID()}",
            element = "#app",
            waitElement = ".warframeFissure"
        )
        warframeDataService.getFissuresData()
        val url = webImgUtil.getImgUrl(imgData)
        context.sender.sendImage(url)
        webImgUtil.deleteImg(imgData)
    }


    @SystemLog(businessName = "获取奸商信息")
    @AParameter
    @Executor(action = "奸商")
    suspend fun findVoidTrader(context: ExecutionContext) {
        if (!translations.ready) {
            context.sender.sendText(PublicExportService.UNAVAILABLE_MESSAGE)
            return
        }
        if (!redisService.hasKey(translations.cacheKey(WF_VOIDTRADER_KEY))) {
            val data = worldStateSource.get()
            parseDataUtil.parseVoidTraders(data["VoidTraders"])
        }

        val voidTraderList = redisService.getValueTyped<List<VoidTrader>>(translations.cacheKey(WF_VOIDTRADER_KEY))
        if (voidTraderList.isNullOrEmpty()) {
            context.sender.sendText("糟糕OωO，虚空商人不见了，请联系管理员进行检查")
            return
        }

        val activeVoidList = voidTraderList.filter { it.isActive == true }
        if (activeVoidList.isEmpty()) {
            val arrivalTime = voidTraderList.first().expiry?.minus(Duration.ofDays(2))
            val etaTime = formatDuration(Duration.between(TimeUtils.getInstantNow(), arrivalTime))
            context.sender.sendText("虚空商人仍未回归...\n也许将在 $etaTime 后抵达 ${voidTraderList.first().node}")
            return
        }

        // 当Redis中商人的缓存存在时，直接发送图片
        // 使用协程并发处理多个激活的虚空商人
        coroutineScope {
            activeVoidList.map { voidTrader ->
                async(Dispatchers.IO) {
                    val originalIndex = voidTraderList.indexOf(voidTrader)

                    val imgData = WebImgUtil.ImgData(
                        // 将准确的原始索引传递给前端接口
                        url = "http://${webImgUtil.frontendAddress}/voidTrader?activeNum=$originalIndex",
                        imgName = "voidTrader-${UUID.randomUUID()}-$originalIndex",
                        element = "#app",
                        waitElement = ".warframeVoidTrader"
                    )
                    val url = webImgUtil.getImgUrl(imgData)
                    context.sender.sendImage(url)
                    webImgUtil.deleteImg(imgData)
                }
            }.awaitAll()
        }
    }

    @SystemLog(businessName = "获取钢铁之路兑换信息")
    @AParameter
    @Executor(action = "钢铁")
    suspend fun getSteelPath(context: ExecutionContext) {
        if (!translations.ready) {
            context.sender.sendText(PublicExportService.UNAVAILABLE_MESSAGE)
            return
        }
        val imgData = WebImgUtil.ImgData(
            url = "http://${webImgUtil.frontendAddress}/steelPath",
            imgName = "steelPath-${UUID.randomUUID()}",
            element = "#app",
            waitElement = ".wfSteelPath"
        )

        val url = webImgUtil.getImgUrl(imgData)
        context.sender.sendImage(url)
        webImgUtil.deleteImg(imgData)
    }

    @SystemLog(businessName = "获取日突击信息")
    @AParameter
    @Executor(action = "突击")
    suspend fun getSortie(context: ExecutionContext) {
        if (!translations.ready) {
            context.sender.sendText(PublicExportService.UNAVAILABLE_MESSAGE)
            return
        }
        if (!redisService.hasKey(translations.cacheKey(WF_SORTIE_KEY))) {
            val data = worldStateSource.get()
            parseDataUtil.parseSorties(data["Sorties"])
        }
        val imgData = WebImgUtil.ImgData(
            url = "http://${webImgUtil.frontendAddress}/sortie",
            imgName = "sortie-${UUID.randomUUID()}",
            element = "#app",
            waitElement = ".warframeSortie"
        )

        val url = webImgUtil.getImgUrl(imgData)
        context.sender.sendImage(url)
        webImgUtil.deleteImg(imgData)
    }

    @SystemLog(businessName = "获取执刑官信息")
    @AParameter
    @Executor(action = "执(?:行|刑)官")
    suspend fun getArchonHunt(context: ExecutionContext) {
        if (!translations.ready) {
            context.sender.sendText(PublicExportService.UNAVAILABLE_MESSAGE)
            return
        }
        if (!redisService.hasKey(translations.cacheKey(WF_ARCHONHUNT_KEY))) {
            val data = worldStateSource.get()
            parseDataUtil.parseArchonHunt(data["LiteSorties"])
        }

        val imgData = WebImgUtil.ImgData(
            url = "http://${webImgUtil.frontendAddress}/archonHunt",
            imgName = "archonHuntInfo-${UUID.randomUUID()}",
            element = "#app",
            waitElement = ".warframeArchonHunt"
        )

        val url = webImgUtil.getImgUrl(imgData)
        context.sender.sendImage(url)
        webImgUtil.deleteImg(imgData)
    }

    @SystemLog(businessName = "获取午夜电波信息")
    @AParameter
    @Executor(action = "\\b(电波|午夜电波)\\b")
    suspend fun getNightWave(context: ExecutionContext) {
        if (!translations.ready) {
            context.sender.sendText(PublicExportService.UNAVAILABLE_MESSAGE)
            return
        }
        if (!redisService.hasKey(translations.cacheKey(WF_NIGHTWAVE_KEY))) {
            val data = worldStateSource.get()
            parseDataUtil.parseNightWave(data["SeasonInfo"])
        }

        val imgData = WebImgUtil.ImgData(
            url = "http://${webImgUtil.frontendAddress}/nightWave",
            imgName = "nightWave-${UUID.randomUUID()}",
            element = "#app",
            waitElement = ".warframeNightWave"
        )

        val url = webImgUtil.getImgUrl(imgData)
        context.sender.sendImage(url)
        webImgUtil.deleteImg(imgData)
    }

    @SystemLog(businessName = "获取火卫二循环信息")
    @AParameter
    @Executor(action = "\\b(火卫二状态|火星状态|火星平原状态|火卫二平原状态|火卫二平原|火星平原)\\b")
    suspend fun phobosStatus(context: ExecutionContext?): String = sendCycleStatus(context, "cambion")

    @SystemLog(businessName = "获取夜灵平原昼夜循环信息")
    @AParameter
    @Executor(action = "\\b(地球平原状态|希图斯状态|夜灵平原状态|地球平原|夜灵平原)\\b")
    suspend fun cetusCycle(context: ExecutionContext?): String = sendCycleStatus(context, "cetus")

    @SystemLog(businessName = "获取地球昼夜循环信息")
    @AParameter
    @Executor(action = "\\b(地球状态|地球时间|地球)\\b")
    suspend fun earthCycle(context: ExecutionContext?): String = sendCycleStatus(context, "earth")

    @SystemLog(businessName = "获取金星冷热循环信息")
    @AParameter
    @Executor(action = "\\b(金星状态|金星平原状态|福尔图娜状态|福尔图娜平原状态|金星平原|福尔图娜)\\b")
    suspend fun venusStatus(context: ExecutionContext?): String = sendCycleStatus(context, "vallis")

    private fun cycleMessages(): Map<String, String> {
        val titles = linkedMapOf(
            "cambion" to "火卫二平原", "cetus" to "希图斯平原",
            "earth" to "地球昼夜", "vallis" to "金星平原"
        )
        val statuses = try {
            wfUtil.getCycleStatuses()
        } catch (error: WorldStateUnavailableException) {
            return titles.mapValues { error.message.orEmpty() }
        }
        return titles.mapValues { (id, title) ->
            val status = statuses[id]
            if (status == null) "暂时无法获取${title}状态，官方尚未发布有效的周期数据，请稍后重试"
            else "当前${title}的状态为 ${status.displayState}\n" +
                    "开始时间:${status.activation}\n" +
                    "结束时间:${status.expiry}\n" +
                    "剩余:${status.timeLeft}"
        }
    }

    private suspend fun sendCycleStatus(context: ExecutionContext?, id: String): String {
        val message = cycleMessages().getValue(id)
        context?.sender?.sendText(message)
        return message
    }

    @SystemLog(businessName = "获取全部平原循环信息")
    @AParameter
    @Executor(action = "\\b(平原|全部平原|平原时间)\\b")
    suspend fun allPlain(context: ExecutionContext) {
        context.sender.sendText(cycleMessages().values.distinct().joinToString("\n\n"))
    }

    @SystemLog(businessName = "获取入侵列表")
    @AParameter
    @Executor(action = "\\b入侵\\b")
    suspend fun invasions(context: ExecutionContext) {
        if (!translations.ready) {
            context.sender.sendText(PublicExportService.UNAVAILABLE_MESSAGE)
            return
        }
        if (!redisService.hasKey(translations.cacheKey(WF_INVASIONS_KEY))) {
            val data = worldStateSource.get()
            parseDataUtil.parseInvasions(data["Invasions"])
        }
        val imgData = WebImgUtil.ImgData(
            url = "http://${webImgUtil.frontendAddress}/invasions",
            imgName = "invasions-${UUID.randomUUID()}",
            element = "#app",
            waitElement = ".warframeInvasions"
        )

        val url = webImgUtil.getImgUrl(imgData)
        context.sender.sendImage(url)
        webImgUtil.deleteImg(imgData)
        System.gc()
    }

    @SystemLog(businessName = "获取本周灵化信息")
    @AParameter
    @Executor(action = "\\b(本周灵化|这周灵化|灵化|回廊|钢铁回廊|本周回廊)\\b")
    suspend fun incarnon(context: ExecutionContext) {
        if (!translations.ready) {
            context.sender.sendText(PublicExportService.UNAVAILABLE_MESSAGE)
            return
        }
        if (!redisService.hasKey(translations.cacheKey(WF_INCARNON_KEY))) {
            parseDataUtil.parseIncarnon()
        }

        val imgData = WebImgUtil.ImgData(
            url = "http://${webImgUtil.frontendAddress}/incarnon",
            imgName = "incarnon-${UUID.randomUUID()}",
            element = "#app",
            waitElement = ".warframeIncarnon"
        )

        val url = webImgUtil.getImgUrl(imgData)
        context.sender.sendImage(url)
        webImgUtil.deleteImg(imgData)
        System.gc()
    }

    @SystemLog(businessName = "获取双衍平原状态信息")
    @AParameter
    @Executor(action = "\\b(双衍|双衍平原|双衍状态|双衍平原状态|回廊状态|虚空平原状态|复眠螺旋|复眠螺旋状态|王境状态)\\b")
    suspend fun moodSpirals(context: ExecutionContext) {
        if (!translations.ready) {
            context.sender.sendText(PublicExportService.UNAVAILABLE_MESSAGE)
            return
        }
        if (wfUtil.getMoodSpirals() == null) {
            context.sender.sendText(WarframeRespEnum.SPIRALS_ERROR.message)
            return
        }
        // 生成和发送图像
        val imgData = WebImgUtil.ImgData(
            url = "http://${webImgUtil.frontendAddress}/spirals",
            imgName = "spirals-${UUID.randomUUID()}",
            element = "#app",
            waitElement = ".warframeSpirals"
        )

        val url = webImgUtil.getImgUrl(imgData)
        context.sender.sendImage(url)
        webImgUtil.deleteImg(imgData)
        System.gc()
    }

    @SystemLog(businessName = "获取信条/终幕武器轮换信息")
    @AParameter
    @Executor(action = "\\b(佩兰|信条|终幕|信条轮换|终幕轮换)\\b")
    suspend fun weaponRotation(context: ExecutionContext) {
        val reports = withContext(Dispatchers.IO) { wikiWeaponRotations.reports() }
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("Asia/Shanghai"))
        val elements = mapOf("Electricity" to "电击", "Cold" to "冰冻", "Heat" to "火焰", "Impact" to "冲击",
            "Magnetic" to "磁力", "Radiation" to "辐射", "Toxin" to "毒素")
        val message = reports.joinToString("\n\n") { report ->
            buildString {
                appendLine("${report.title}轮换${report.batch?.let { " · 批次 $it" }.orEmpty()}")
                appendLine("下次刷新：${formatter.format(Instant.ofEpochMilli(report.expiry))}")
                appendLine("剩余：${formatDuration(Duration.ofMillis((report.expiry - System.currentTimeMillis()).coerceAtLeast(0)))}")
                report.wikiUpdatedAt?.let { appendLine("Wiki 修订：${formatter.format(Instant.ofEpochMilli(it))}") }
                appendLine(report.note)
                report.weapons.forEach { offer ->
                    val name = if (translations.ready) translations.name(offer.name) else offer.name
                    val bonus = java.math.BigDecimal.valueOf(offer.bonus).stripTrailingZeros().toPlainString()
                    appendLine("$name · ${elements[offer.element] ?: offer.element} +$bonus%")
                }
            }
        }
        context.sender.sendText(message)
    }

    @SystemLog(businessName = "获取圣殿结合仪式目标")
    @AParameter
    @Executor(action = "\\b(结合仪式|结合|结合目标|大黄脸)\\b")
    suspend fun sanctuarySynthesisTargets(context: ExecutionContext) {
        if (!translations.ready) {
            context.sender.sendText(PublicExportService.UNAVAILABLE_MESSAGE)
            return
        }
        if (!redisService.hasKey(translations.cacheKey(WF_SIMARIS_KEY))) {
            val data = worldStateSource.get()
            parseDataUtil.parseSimaris(data["LibraryInfo"])
        }

        val imgData = WebImgUtil.ImgData(
            url = "http://${webImgUtil.frontendAddress}/simaris",
            imgName = "simaris-${UUID.randomUUID()}",
            element = "#app",
            waitElement = ".warframeSimaris"
        )

        val url = webImgUtil.getImgUrl(imgData)
        context.sender.sendImage(url)
        webImgUtil.deleteImg(imgData)
    }

    @SystemLog(businessName = "获取DE紫卡数据")
    @AParameter
    @Executor(action = "\\b(紫卡价格|紫卡排行|紫卡|紫卡均价)(\\s+.*)?$")
    suspend fun getRivenRanking(context: ExecutionContext, matcher: Matcher) {
        if (!translations.ready) {
            context.sender.sendText(PublicExportService.UNAVAILABLE_MESSAGE)
            return
        }
        if (!redisService.hasKey(translations.cacheKey(WF_RIVEN_UN_REROLLED_KEY)) || !redisService.hasKey(
                translations.cacheKey(
                    WF_RIVEN_REROLLED_KEY
                )
            )
        ) {
            parseDataUtil.parseWeeklyRiven()
        }

        val params = matcher.group(2)?.trim() ?: ""
        val urlParams = wfUtil.parseRivenParams(params)

        val currentMonday = LocalDate.now()
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .toString()

        val imgData = WebImgUtil.ImgData(
            url = "http://${webImgUtil.frontendAddress}/allRivenPrice$urlParams",
            imgName = "allRivenPrice-${currentMonday}-${translations.cacheTag()}",
            element = "#app",
            waitElement = ".warframeRivenAllPrice"
        )
        val url = webImgUtil.getImgUrl(imgData)
        context.sender.sendImage(url)
    }


    @SystemLog(businessName = "获取科研任务信息")
    @AParameter
    @Executor(action = "\\b(科研|时光科研|深层科研)\\b")
    suspend fun getConquest(context: ExecutionContext) {
        if (!translations.ready) {
            context.sender.sendText(PublicExportService.UNAVAILABLE_MESSAGE)
            return
        }
        if (!redisService.hasKey(translations.cacheKey(WF_CONQUEST_KEY))) {
            val data = worldStateSource.get()
            parseDataUtil.parseConquestArray(data["Conquests"])
        }

        val imgData = WebImgUtil.ImgData(
            url = "http://${webImgUtil.frontendAddress}/conquest",
            imgName = "conquest-${UUID.randomUUID()}",
            element = "#app",
            waitElement = ".warframeConquest"
        )

        val url = webImgUtil.getImgUrl(imgData)
        context.sender.sendImage(url)
        webImgUtil.deleteImg(imgData)
    }


    @SystemLog(businessName = "获取1999日历信息")
    @AParameter
    @Executor(action = "\\b(日历|1999日历|石榴2代|石榴电脑)\\b")
    suspend fun getCalendar(context: ExecutionContext) {
        if (!translations.ready) {
            context.sender.sendText(PublicExportService.UNAVAILABLE_MESSAGE)
            return
        }
        if (!redisService.hasKey(translations.cacheKey(WF_CALENDAR_KEY))) {
            val data = worldStateSource.get()
            parseDataUtil.parseCalendarArray(data["KnownCalendarSeasons"])
        }

        val imgData = WebImgUtil.ImgData(
            url = "http://${webImgUtil.frontendAddress}/calendar",
            imgName = "calendar-${UUID.randomUUID()}",
            element = "#app",
            waitElement = ".warframeCalendar"
        )

        val url = webImgUtil.getImgUrl(imgData)
        context.sender.sendImage(url)
        webImgUtil.deleteImg(imgData)
    }

    @SystemLog(businessName = "获取周常任务信息")
    @AParameter
    @Executor(action = "\\b(周常|每周任务)\\b")
    suspend fun getWeekly(context: ExecutionContext) {
        if (!translations.ready) {
            context.sender.sendText(PublicExportService.UNAVAILABLE_MESSAGE)
            return
        }
        context.sender.sendImage(wfUtil.getWeeklyImgUrl())
    }
}