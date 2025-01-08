package bot.demo.txbot.warframe

import bot.demo.txbot.common.botUtil.BotUtils.Context
import bot.demo.txbot.common.logAop.SystemLog
import bot.demo.txbot.common.utils.HttpUtil
import bot.demo.txbot.common.utils.LoggerUtils.logInfo
import bot.demo.txbot.common.utils.OtherUtil.STConversion.toMd5
import bot.demo.txbot.other.distribute.annotation.AParameter
import bot.demo.txbot.other.distribute.annotation.ActionService
import bot.demo.txbot.other.distribute.annotation.Executor
import bot.demo.txbot.warframe.database.WfLexiconEntity
import bot.demo.txbot.warframe.database.WfLexiconService
import bot.demo.txbot.warframe.database.WfRivenEntity
import bot.demo.txbot.warframe.database.WfRivenService
import bot.demo.txbot.warframe.database.entity.WfMarketItemEntity
import bot.demo.txbot.warframe.database.service.WfMarketItemService
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component


/**
 * @description: Warframe 翻译词库
 * @author Nature Zero
 * @date 2024/5/20 下午11:38
 */
@Component
@ActionService
class WfTranslateLexicon {
    @Autowired
    lateinit var wfLexiconService: WfLexiconService

    @Autowired
    lateinit var wfRivenService: WfRivenService

    @Autowired
    lateinit var wfMarketItemService: WfMarketItemService

    /**
     * 获取Json数据并进行格式化
     *
     * @param lexiconMap 词库Map
     * @return 格式化后的词库List
     */
    fun getLexiconList(lexiconMap: MutableMap<String, WfLexiconEntity>): MutableList<WfLexiconEntity> {
        updateLexicon(lexiconMap, WARFRAME_MARKET_LOCATION, LANGUAGE_EN_HANS, "locations", "system_name")
        updateLexicon(
            lexiconMap,
            WARFRAME_MARKET_LOCATION,
            LANGUAGE_ZH_HANS,
            "locations",
            "system_name",
            isChinese = true
        )

        updateStatusLexicon(lexiconMap, WARFRAME_STATUS_ITEM, "en")
        updateStatusLexicon(lexiconMap, WARFRAME_STATUS_ITEM, "zh", true)

        return lexiconMap.values.toMutableList()
    }

    /**
     * 获取Json数据并进行格式化
     *
     * @param lexiconMap 词库Map
     * @return 格式化后的词库List
     */
    fun getMarketItem(lexiconMap: MutableMap<String, WfMarketItemEntity>): MutableList<WfMarketItemEntity> {
        updateLexicon2(lexiconMap, WARFRAME_MARKET_ITEMS, LANGUAGE_EN_HANS, "items", "item_name")
        updateLexicon2(lexiconMap, WARFRAME_MARKET_ITEMS, LANGUAGE_ZH_HANS, "items", "item_name", isChinese = true)


        return lexiconMap.values.toMutableList()
    }


    /**
     * 获取紫卡列表
     *
     * @param rivenMap 词库Map
     * @return 紫卡列表
     */
    fun getRivenList(rivenMap: MutableMap<String, WfRivenEntity>): MutableList<WfRivenEntity> {
        updateRiven(rivenMap, WARFRAME_MARKET_RIVEN_ITEMS, LANGUAGE_EN_HANS, "items", "item_name")
        updateRiven(rivenMap, WARFRAME_MARKET_RIVEN_ITEMS, LANGUAGE_ZH_HANS, "items", "item_name", isChinese = true)
        updateRiven(rivenMap, WARFRAME_MARKET_RIVEN_ATTRIBUTES, LANGUAGE_EN_HANS, "attributes", "effect")
        updateRiven(
            rivenMap,
            WARFRAME_MARKET_RIVEN_ATTRIBUTES,
            LANGUAGE_ZH_HANS,
            "attributes",
            "effect",
            isChinese = true
        )

        return rivenMap.values.toMutableList()
    }

    /**
     * 获取赤毒紫卡列表
     *
     * @param richMap 词库Map
     * @return 赤毒紫卡列表
     */
    fun getLichList(richMap: MutableMap<String, WfRivenEntity>): MutableList<WfRivenEntity> {
        updateLich(richMap, WARFRAME_MARKET_LICH_WEAPONS, LANGUAGE_EN_HANS, "weapons", "item_name")
        updateLich(richMap, WARFRAME_MARKET_LICH_WEAPONS, LANGUAGE_ZH_HANS, "weapons", "item_name", isChinese = true)
        updateLich(richMap, WARFRAME_MARKET_SISTER_WEAPONS, LANGUAGE_EN_HANS, "weapons", "item_name")
        updateLich(richMap, WARFRAME_MARKET_SISTER_WEAPONS, LANGUAGE_ZH_HANS, "weapons", "item_name", isChinese = true)

        return richMap.values.toMutableList()
    }

    /**
     * 更新紫卡词库
     *
     * @param lexiconMap 词库Map
     * @param url 请求URL
     * @param language 请求语言
     * @param listKey 词库列表Key
     * @param nameKey 词库名称Key
     * @param isChinese 是否为中文
     */
    private fun updateLexicon(
        lexiconMap: MutableMap<String, WfLexiconEntity>,
        url: String,
        language: MutableMap<String, Any>,
        listKey: String,
        nameKey: String,
        isChinese: Boolean = false,
        inMarket: Int = 0
    ) {
        val items = HttpUtil.doGetJson(url = url, headers = language)["payload"][listKey]
        items.forEach { item ->
            val id = item["id"].textValue()
            val entity = lexiconMap[id] ?: WfLexiconEntity(
                id = id,
                enItemName = if (!isChinese) item[nameKey].textValue() else "",
                urlName = item["url_name"].textValue(),
                zhItemName = if (isChinese) item[nameKey].textValue() else "",
                inMarket = inMarket
            )
            if (isChinese) {
                entity.zhItemName = item[nameKey].textValue()
            } else {
                entity.enItemName = item[nameKey].textValue()
            }
            lexiconMap[id] = entity
        }
    }

    /**
     * 更新紫卡词库
     *
     * @param marketItemMap 词库Map
     * @param url 请求URL
     * @param language 请求语言
     * @param listKey 词库列表Key
     * @param nameKey 词库名称Key
     * @param isChinese 是否为中文
     */
    private fun updateLexicon2(
        marketItemMap: MutableMap<String, WfMarketItemEntity>,
        url: String,
        language: MutableMap<String, Any>,
        listKey: String,
        nameKey: String,
        isChinese: Boolean = false,
    ) {
        val items = HttpUtil.doGetJson(url = url, headers = language)["payload"][listKey]
        items.forEach { item ->
            val id = item["id"].textValue()
            val entity = marketItemMap[id] ?: WfMarketItemEntity(
                id = id,
                enName = if (!isChinese) item[nameKey].textValue() else null,
                urlName = item["url_name"].textValue(),
                zhName = if (isChinese) item[nameKey].textValue() else null,
                useCount = 0
            )
            if (isChinese) {
                entity.zhName = item[nameKey].textValue()
            } else {
                entity.enName = item[nameKey].textValue()
            }
            marketItemMap[id] = entity
        }
    }

    fun updateStatusLexicon(
        lexiconMap: MutableMap<String, WfLexiconEntity>,
        url: String,
        languageType: String,
        isChinese: Boolean = false
    ) {
        val items = HttpUtil.doGetJson(url = url, params = mapOf("language" to languageType))
        items.forEach { item ->
            val itemName = item["name"].textValue()
            // 检查itemName是否已存在于lexiconMap的zhItemName中
            if (!lexiconMap.values.any { it.zhItemName?.contains(itemName) == true || it.enItemName?.contains(itemName) == true }) {
                // 确保itemName不在lexiconMap的zhItemNames中后，再执行更新逻辑
                val urlName = if (item.has("imageName")) item["imageName"].textValue().split(".")[0].replace(
                    "-",
                    "_"
                ) else itemName
                val id = urlName.toMd5()
                val entity = lexiconMap[id] ?: WfLexiconEntity(
                    id = id,
                    enItemName = if (!isChinese) itemName else "",
                    urlName = urlName,
                    zhItemName = if (isChinese) itemName else "",
                    inMarket = 0
                )
                if (isChinese) {
                    entity.zhItemName = itemName
                } else {
                    entity.enItemName = itemName
                }
                lexiconMap[id] = entity
            }
        }
    }

    /**
     * 更新紫卡词库
     *
     * @param rivenMap 词库Map
     * @param url 请求URL
     * @param language 语言
     * @param listKey 词库列表Key
     * @param nameKey 物品名称Key
     * @param isChinese 是否为中文
     */
    private fun updateRiven(
        rivenMap: MutableMap<String, WfRivenEntity>,
        url: String,
        language: MutableMap<String, Any>,
        listKey: String,
        nameKey: String,
        isChinese: Boolean = false
    ) {
        val items = HttpUtil.doGetJson(url = url, headers = language)["payload"][listKey]
        items.forEach { item ->
            val id = item["id"].textValue()
            val entity = rivenMap[id] ?: WfRivenEntity(
                id = id,
                rGroup = item["group"].textValue(),
                enName = if (!isChinese) item[nameKey].textValue() else "",
                urlName = item["url_name"].textValue(),
                zhName = if (isChinese) item[nameKey].textValue() else "",
                attributesBool = if (url == WARFRAME_MARKET_RIVEN_ATTRIBUTES) 1 else 0
            )
            if (isChinese) {
                entity.zhName = item[nameKey].textValue()
            } else {
                entity.enName = item[nameKey].textValue()
            }
            rivenMap[id] = entity
        }
    }

    /**
     * 更新玄骸武器词库
     *
     * @param richMap 词库Map
     * @param url 请求URL
     * @param language 语言
     * @param listKey 词库列表Key
     * @param nameKey 物品名称Key
     * @param isChinese 是否为中文
     */
    fun updateLich(
        richMap: MutableMap<String, WfRivenEntity>,
        url: String,
        language: MutableMap<String, Any>,
        listKey: String,
        nameKey: String,
        isChinese: Boolean = false
    ) {
        val items = HttpUtil.doGetJson(url = url, headers = language)["payload"][listKey]
        items.forEach { item ->
            val id = item["id"].textValue()
            val entity = richMap[id] ?: WfRivenEntity(
                id = id,
                rGroup = "lich",
                enName = if (!isChinese) item[nameKey].textValue() else "",
                urlName = item["url_name"].textValue(),
                zhName = if (isChinese) item[nameKey].textValue() else "",
                attributesBool = 2
            )

            if (isChinese) {
                entity.zhName = item[nameKey].textValue()
            } else {
                entity.enName = item[nameKey].textValue()
            }
            richMap[id] = entity
        }
    }


    @SystemLog(businessName = "更新Warframe词库")
    @OptIn(DelicateCoroutinesApi::class)
    @AParameter
    @Executor(action = "更新词库")
    fun upDataWfTranslateLexicon(context: Context) {
        val lexiconMap: MutableMap<String, WfLexiconEntity> = mutableMapOf()
        val rivenMap: MutableMap<String, WfRivenEntity> = mutableMapOf()
        val lichMap: MutableMap<String, WfRivenEntity> = mutableMapOf()
        val marketItemMap: MutableMap<String, WfMarketItemEntity> = mutableMapOf()

        GlobalScope.launch {
            try {
                // 获取中英文JSON数据并解析
                context.sendMsg("因本次更新数据量较大，预计花费5-10分钟不等，请耐心等待")

                // 使用async并行执行插入操作
                val marketJob = async {
                    wfMarketItemService.updateMarketItem(getMarketItem(marketItemMap))
                    logInfo("Market更新完成")
                    marketItemMap.clear()
                }

                val lexiconJob = async {
                    wfLexiconService.insertLexicon(getLexiconList(lexiconMap))
                    logInfo("词库更新完成")
                    lexiconMap.clear()
                }
                val rivenJob = async {
                    wfRivenService.insertRiven(getRivenList(rivenMap))
                    logInfo("紫卡词库更新完成")
                    rivenMap.clear()
                }
                val lichJob = async {
                    wfRivenService.insertRiven(getLichList(lichMap))
                    logInfo("玄骸武器词库更新完成")
                    lichMap.clear()
                }

                // 等待所有任务完成
                marketJob.await()
                lexiconJob.await()
                rivenJob.await()
                lichJob.await()


                context.sendMsg("词库更新完成")
            } finally {
                // 显式地将变量置空
                lexiconMap.clear()
                rivenMap.clear()
                lichMap.clear()
                System.gc()
            }
        }
    }
}