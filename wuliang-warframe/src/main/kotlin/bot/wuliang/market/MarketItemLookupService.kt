package bot.wuliang.market

import bot.wuliang.adapter.context.ExecutionContext
import bot.wuliang.config.WfMarketConfig
import bot.wuliang.entity.WfMarketItemEntity
import bot.wuliang.otherUtil.OtherUtil
import bot.wuliang.redis.RedisService
import bot.wuliang.service.WfMarketItemService
import bot.wuliang.service.WfRivenService
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

/** 负责物品名称查询与模糊匹配提示，数据库访问与名称缓存集中在此处。 */
@Service
class MarketItemLookupService(
    private val redisService: RedisService,
    private val wfMarketItemService: WfMarketItemService,
    private val wfRivenService: WfRivenService,
    @Qualifier("otherUtil") private val otherUtil: OtherUtil,
) {

    /** 使用关键词中的汉字查找紫卡名称候选，并向 [context] 发送相近名称或未命中提示。 */
    suspend fun handleFuzzySearch(context: ExecutionContext, itemNameKey: String) {
        val fuzzyList = mutableSetOf<String>()
        // 与 wm 一样，不把英文和数字拆成单字符兜底；标点、空白和 SQL 通配符也不参与。
        val queryCharacters = itemNameKey
            .filter { Character.UnicodeScript.of(it.code) == Character.UnicodeScript.HAN }.toSet()
        queryCharacters.forEach { char ->
            wfRivenService.superFuzzyQuery(char.toString())
                ?.forEach { it?.zhName?.let { name -> fuzzyList.add(name) } }
        }

        if (fuzzyList.isNotEmpty()) {
            otherUtil.findMatchingStrings(itemNameKey, fuzzyList.toList()).let {
                context.sender.sendText("未找到该物品,也许你想找的是:${it.joinToString(", ")}")
            }
        } else {
            context.sender.sendText("未找到任何匹配项")
        }
    }

    /**
     * 优先精确匹配 [key]，再选择模糊匹配的首项，命中后缓存名称查询结果三十天。
     *
     * @return 匹配的市场物品；没有候选时返回 null。
     */
    fun fetchItemEntity(key: String): WfMarketItemEntity? {
        val cacheKey = WfMarketConfig.WF_MARKET_ITEM_LEXICON_KEY_PREFIX + key
        val itemEntity = wfMarketItemService.selectItemByAccurateNature(key)
        if (itemEntity != null) {
            redisService.setValueWithExpiry(cacheKey, itemEntity, 30L, TimeUnit.DAYS)
            return itemEntity
        }

        val marketItemList = wfMarketItemService.getItemByFuzzyMatching(key)
        if (!marketItemList.isNullOrEmpty()) {
            val firstItemEntity = marketItemList.first()
            redisService.setValueWithExpiry(cacheKey, firstItemEntity, 30L, TimeUnit.DAYS)
            return firstItemEntity
        }

        return null
    }
}