package bot.wuliang.scheduled

import bot.wuliang.config.WARFRAME_STATUS_VOID_TRADER
import bot.wuliang.config.WfMarketConfig.WF_VOIDTRADER_KEY
import bot.wuliang.config.WfMarketConfig.WF_VOID_TRADER_COME_KEY
import bot.wuliang.entity.vo.WfStatusVo
import bot.wuliang.httpUtil.HttpUtil
import bot.wuliang.httpUtil.ProxyUtil
import bot.wuliang.otherUtil.OtherUtil.STConversion.turnZhHans
import bot.wuliang.redis.RedisService
import bot.wuliang.service.WfLexiconService
import bot.wuliang.utils.WfStatus.parseDuration
import bot.wuliang.utils.WfStatus.replaceTime
import bot.wuliang.utils.WfUtil
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

@Component
class WfStatusScheduled {
    @Autowired
    private lateinit var proxyUtil: ProxyUtil

    @Autowired
    private lateinit var redisService: RedisService

    @Autowired
    private lateinit var wfUtil: WfUtil

    @Autowired
    private lateinit var wfLexiconService: WfLexiconService

    @Scheduled(cron = "\${warframe.status.trader-cron}")
    fun getVoidTraderData(): WfStatusVo.VoidTraderEntity? {
        // 当Redis中商人的缓存不存在时，尝试通过API获取数据
        if (!redisService.hasKey(WF_VOIDTRADER_KEY)) {
            val traderJson =
                try {
                    HttpUtil.doGetJson(WARFRAME_STATUS_VOID_TRADER, proxy = proxyUtil.randomProxy())
                } catch (e: Exception) {
                    // 如果使用代理的请求失败，则尝试使用无代理方式
                    HttpUtil.doGetJson(WARFRAME_STATUS_VOID_TRADER)
                }

            val startString = traderJson["startString"].asText().replaceTime()
            val endString = traderJson["endString"].asText().replaceTime()
            val location = traderJson["location"].asText().turnZhHans()

            if (traderJson["inventory"].isEmpty) {
                // 向Redis中写入缓存，用于提示
                redisService.setValueWithExpiry(
                    WF_VOID_TRADER_COME_KEY,
                    location,
                    startString.parseDuration(),
                    TimeUnit.SECONDS
                )
                return null
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
                    WF_VOIDTRADER_KEY,
                    voidTraderEntity,
                    endString.parseDuration(),
                    TimeUnit.SECONDS
                )

                return voidTraderEntity
            }
        } else {
            // 从Redis获取缓存数据
            return redisService.getValueTyped<WfStatusVo.VoidTraderEntity>(WF_VOIDTRADER_KEY)
        }
    }
}