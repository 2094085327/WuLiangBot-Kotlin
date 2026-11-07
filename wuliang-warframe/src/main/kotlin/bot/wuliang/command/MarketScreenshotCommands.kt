package bot.wuliang.command

import bot.wuliang.adapter.context.ExecutionContext
import bot.wuliang.aipOcr.AipOcrClient
import bot.wuliang.aipOcr.OcrUnavailableException
import bot.wuliang.botLog.logUtil.LoggerUtils.logError
import bot.wuliang.distribute.annotation.AParameter
import bot.wuliang.distribute.annotation.ActionService
import bot.wuliang.distribute.annotation.Executor
import bot.wuliang.imageProcess.WebImgUtil
import bot.wuliang.jacksonUtil.JacksonUtil
import bot.wuliang.logAop.SystemLog
import bot.wuliang.market.MarketPriceService
import bot.wuliang.market.MarketScreenshotSessions
import bot.wuliang.market.PendingMarketScreenshot
import bot.wuliang.message.BotMessage
import bot.wuliang.message.MessageContinuation
import bot.wuliang.message.handlePendingImages
import bot.wuliang.moudles.WmDucats
import bot.wuliang.riven.analysis.RivenAnalysisSessions
import bot.wuliang.service.WfMarketItemService
import kotlinx.coroutines.*
import org.springframework.stereotype.Component
import java.util.*
import javax.annotation.PreDestroy
import kotlin.time.Duration.Companion.milliseconds

/** 截图价格查询指令，保留两张图片上限与市场请求并发限制。 */
@Component
@ActionService
class MarketScreenshotCommands(
    private val marketPriceService: MarketPriceService,
    private val webImgUtil: WebImgUtil,
    private val aipOcrClient: AipOcrClient,
    private val wfMarketItemService: WfMarketItemService,
    private val sessions: MarketScreenshotSessions,
    private val rivenSessions: RivenAnalysisSessions,
) : MessageContinuation {
    override val commandText = "部件"
    private val wmRateLimiter = kotlinx.coroutines.sync.Semaphore(2)
    private val waitingScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /** 识别最多两张部件截图，限制市场请求并发后查询最低售价并发送价格对比图片。 */
    @SystemLog(businessName = "获取部件在WM的白金价格")
    @AParameter
    @Executor(action = "(?i)\\b(部件|WM价格|WM价格查询)\\b")
    suspend fun getWmPrice(context: ExecutionContext) {
        val imageMessages = context.messages.filterIsInstance<BotMessage.Image>()
        if (imageMessages.size > 2) {
            context.sender.sendText("单次查询图片上限为2张")
            return
        }
        if (sessions.isBusy(context.requestContext)) {
            context.sender.sendText("上一批部件正在查询，请稍候。")
            return
        }
        rivenSessions.cancel(context.requestContext)
        if (imageMessages.isEmpty()) {
            val pending = sessions.begin(context.requestContext)
            remindIfImageMissing(context, pending)
            context.sender.sendText("请在1分钟内发送部件售卖截图，单次最多2张。")
        } else {
            sessions.cancel(context.requestContext)
            process(context, imageMessages)
        }
    }

    private fun remindIfImageMissing(context: ExecutionContext, pending: PendingMarketScreenshot) {
        val requestContext = context.requestContext
        val sender = context.sender
        waitingScope.launch {
            try {
                delay((pending.expiresAtMillis - System.currentTimeMillis()).coerceAtLeast(0).milliseconds)
                if (sessions.expire(requestContext, pending)) {
                    sender.sendText("未接收到部件图片，请重新发送「部件」和图片进行查询。")
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                logError("部件图片等待超时提醒失败", error)
            }
        }
    }

    @PreDestroy
    fun close() {
        waitingScope.cancel()
    }

    override suspend fun handleIfPending(context: ExecutionContext): Boolean = handlePendingImages(
        context = context,
        maxImages = 2,
        tooManyImagesMessage = "单次查询图片上限为2张，请在本次等待时间内重新发送。",
        pending = sessions::pending,
        consume = sessions::consume,
    ) { pictures, _ ->
        process(context, pictures)
    }

    private suspend fun process(context: ExecutionContext, pictures: List<BotMessage.Image>) {
        val token = sessions.acquire(context.requestContext)
        if (token == null) {
            context.sender.sendText("上一批部件正在查询，请稍候。")
            return
        }
        try {
            context.sender.sendText("已收到图片，正在识别部件…")
            withContext(Dispatchers.IO) {
                queryPrices(context, pictures)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: OcrUnavailableException) {
            context.sender.sendText(error.message.orEmpty())
        } catch (error: Exception) {
            logError("部件截图价格查询失败", error)
            context.sender.sendText("部件价格查询暂时失败，请稍后重新发送「部件」和图片。")
        } finally {
            sessions.release(context.requestContext, token)
        }
    }

    private suspend fun queryPrices(context: ExecutionContext, imageMessages: List<BotMessage.Image>) {
        val wordsList = mutableListOf<String>()
        coroutineScope {
            imageMessages.map { imageMessage ->
                async {
                    aipOcrClient.getBasicOcr(mapOf("url" to imageMessage.url))
                }
            }.awaitAll().forEach { result ->
                val root = JacksonUtil.readTree(result.toString())
                val wordsArray = root["words_result"]
                wordsList.addAll(
                    wordsArray.mapNotNull { it["words"]?.asText() }
                        .map { word -> word.replace(Regex("""^\d+\s*[xX×Ⅹ]?\s*"""), "").trim() }
                )
            }
        }
        if (wordsList.isEmpty()) {
            context.sender.sendText("未识别到任何部件名称，请重新发送尽可能清晰的部件售卖图")
            return
        }
        val wfMarketItemEntityList = wfMarketItemService.selectListZhNameList(wordsList)
        if (wfMarketItemEntityList.isNullOrEmpty()) {
            context.sender.sendText("未查询到任何匹配的部件，请重新发送尽可能清晰的部件售卖图")
            return
        }
        context.sender.sendText("正在查询 ${wfMarketItemEntityList.size} 个部件的 WM 最低售价，请耐心等待...")
        val results = coroutineScope {
            wfMarketItemEntityList.map { item ->
                async {
                    wmRateLimiter.acquire()
                    try {
                        val price = marketPriceService.minimumSellPrice(item.urlName!!)
                        val ducats = item.ducats ?: 0
                        val ratio = if (price > 0) ducats.toDouble() / price else 0.0
                        WmDucats(item.zhName, ducats, price, ratio)
                    } finally {
                        wmRateLimiter.release()
                    }
                }
            }.awaitAll()
        }
        val imgData = WebImgUtil.ImgData(
            url = "http://${webImgUtil.frontendAddress}/wmPrice",
            imgName = "wmPrice-${UUID.randomUUID()}",
            data = JacksonUtil.toJsonString(results),
            element = "#app",
            waitElement = ".wmPrice"
        )

        val url = webImgUtil.getImgUrl(imgData)
        context.sender.sendImage(url)
        webImgUtil.deleteImg(imgData = imgData)
    }
}