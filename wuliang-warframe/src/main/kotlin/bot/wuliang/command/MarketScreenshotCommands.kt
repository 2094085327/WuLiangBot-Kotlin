package bot.wuliang.command

import bot.wuliang.adapter.context.ExecutionContext
import bot.wuliang.aipOcr.AipOcrClient
import bot.wuliang.distribute.annotation.AParameter
import bot.wuliang.distribute.annotation.ActionService
import bot.wuliang.distribute.annotation.Executor
import bot.wuliang.imageProcess.WebImgUtil
import bot.wuliang.jacksonUtil.JacksonUtil
import bot.wuliang.logAop.SystemLog
import bot.wuliang.market.MarketPriceService
import bot.wuliang.message.BotMessage
import bot.wuliang.moudles.WmDucats
import bot.wuliang.service.WfMarketItemService
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.springframework.stereotype.Component
import java.util.*

/** 截图价格查询指令，保留两张图片上限与市场请求并发限制。 */
@Component
@ActionService
class MarketScreenshotCommands(
    private val marketPriceService: MarketPriceService,
    private val webImgUtil: WebImgUtil,
    private val aipOcrClient: AipOcrClient,
    private val wfMarketItemService: WfMarketItemService,
) {
    private val wmRateLimiter = kotlinx.coroutines.sync.Semaphore(2)

    /** 识别最多两张部件截图，限制市场请求并发后查询最低售价并发送价格对比图片。 */
    @SystemLog(businessName = "获取部件在WM的白金价格")
    @AParameter
    @Executor(action = "(?i)\\b(部件|WM价格|WM价格查询)\\b")
    suspend fun getWmPrice(context: ExecutionContext) {
        val imageMessages = context.messages.filterIsInstance<BotMessage.Image>()

        if (imageMessages.isEmpty()) {
            context.sender.sendText("请发送 'WM价格'+'查询部件的售卖部分的截图' 来查询部件在WM的白金价格")
            return
        }

        if (imageMessages.size > 2) {
            context.sender.sendText("单次查询图片上限为2张")
            return
        }
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