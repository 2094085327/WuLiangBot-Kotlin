package bot.wuliang.command

import bot.wuliang.adapter.context.ExecutionContext
import bot.wuliang.config.WfMarketConfig.WF_VOIDTRADER_KEY
import bot.wuliang.distribute.annotation.AParameter
import bot.wuliang.distribute.annotation.ActionService
import bot.wuliang.distribute.annotation.Executor
import bot.wuliang.imageProcess.WebImgUtil
import bot.wuliang.logAop.SystemLog
import bot.wuliang.moudles.VoidTrader
import bot.wuliang.redis.RedisService
import bot.wuliang.translation.PublicExportService
import bot.wuliang.utils.TimeUtils
import bot.wuliang.utils.TimeUtils.formatDuration
import bot.wuliang.worldstate.service.TraderQueryService
import bot.wuliang.worldstate.source.WorldStateSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.springframework.stereotype.Component
import java.time.Duration
import java.util.*

/** 虚空商人指令入口，展示到访状态并生成商品图片。 */
@Component
@ActionService
class TraderCommands(
    private val traderQueryService: TraderQueryService,
    private val worldStateSource: WorldStateSource,
    private val translations: PublicExportService,
    private val webImgUtil: WebImgUtil,
    private val redisService: RedisService,
) {

    /** 查询商人到访状态并生成商品图片；结合到访时间处理尚未到访或库存不可用的情况。 */
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
            traderQueryService.getVoidTraders(data["VoidTraders"])
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
}