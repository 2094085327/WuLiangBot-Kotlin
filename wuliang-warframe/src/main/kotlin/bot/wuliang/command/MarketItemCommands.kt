package bot.wuliang.command

import bot.wuliang.adapter.context.ExecutionContext
import bot.wuliang.config.WfMarketConfig.WF_MARKET_ITEM_LEXICON_KEY_PREFIX
import bot.wuliang.distribute.annotation.AParameter
import bot.wuliang.distribute.annotation.ActionService
import bot.wuliang.distribute.annotation.Executor
import bot.wuliang.entity.WfMarketItemEntity
import bot.wuliang.logAop.SystemLog
import bot.wuliang.market.MarketItemLookupService
import bot.wuliang.market.MarketOrderService
import bot.wuliang.otherUtil.OtherUtil
import bot.wuliang.redis.RedisService
import bot.wuliang.respEnum.WarframeRespEnum
import bot.wuliang.service.WfMarketItemService
import bot.wuliang.utils.PagedCommand
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Component
import java.util.regex.Matcher

/** 市场物品指令，名称查询和订单分页由市场服务处理。 */
@Component
@ActionService
class MarketItemCommands(
    private val marketOrderService: MarketOrderService,
    private val marketItemLookupService: MarketItemLookupService,
    @Qualifier("otherUtil") private val otherUtil: OtherUtil,
    private val redisService: RedisService,
    private val wfMarketItemService: WfMarketItemService,
) {

    /** 解析市场物品名称、模组等级和页码；匹配物品后发送对应卖单消息。 */
    @SystemLog(businessName = "获取WM市场物品信息")
    @AParameter
    @Executor(action = "(?i)\\bwm\\s*(\\S+.*)$")
    suspend fun getMarketItem(context: ExecutionContext, matcher: Matcher) {
        val pagedCommand = PagedCommand.parse(matcher.group(1))
        val page = pagedCommand.requestedPage
        val key = pagedCommand.content
        val regex = """(\d+)(?=级)|(满级)""".toRegex()
        val matchResult = regex.find(key)
        val level = matchResult?.value

        // 移除匹配到的部分并去除多余的空格
        val cleanKey = matchResult?.let { key.replace("${it.value}级", "").replace("满级", "").trim() } ?: key
        val redisKey = WF_MARKET_ITEM_LEXICON_KEY_PREFIX + cleanKey

        // 尝试从Redis获取数据
        val lexiconEntity = redisService.getValueTyped<WfMarketItemEntity>(redisKey)
        if (lexiconEntity !=
            null
        ) {
            marketOrderService.sendMarketItemInfo(context, lexiconEntity, level, page)
            return
        }

        // Redis中没有数据，从数据库中查询
        val itemEntity = marketItemLookupService.fetchItemEntity(cleanKey)
            ?: run {
                // 模糊查询
                val fuzzyList = cleanKey
                    .asSequence()
                    .map { it.toString() }
                    .filter {
                        val c = it[0]
                        when {
                            c in 'a'..'z' -> false
                            c in 'A'..'Z' -> false
                            c.isDigit() -> false
                            else -> true
                        }
                    }
                    .flatMap {
                        wfMarketItemService.fuzzyQuery(it).asSequence().mapNotNull { item -> item?.zhName }
                    }
                    .distinct()
                    .toList()
                if (fuzzyList.isNotEmpty()) {
                    otherUtil.findMatchingStrings(cleanKey, fuzzyList).let {
                        context.sender.sendText(WarframeRespEnum.SEARCH_NOT_FOUND.message + it.joinToString(", "))
                    }
                } else context.sender.sendText(WarframeRespEnum.SEARCH_MATCH_NOT_FOUND.message)
                return
            }
        marketOrderService.sendMarketItemInfo(context, itemEntity, level, page)
        return
    }
}