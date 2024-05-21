package bot.demo.txbot.warframe

import bot.demo.txbot.common.utils.HttpUtil
import bot.demo.txbot.warframe.database.WfLexiconEntity
import bot.demo.txbot.warframe.database.WfLexiconService
import bot.demo.txbot.warframe.database.WfRivenEntity
import bot.demo.txbot.warframe.database.WfRivenService
import com.mikuac.shiro.annotation.AnyMessageHandler
import com.mikuac.shiro.annotation.MessageHandlerFilter
import com.mikuac.shiro.annotation.common.Shiro
import com.mikuac.shiro.core.Bot
import com.mikuac.shiro.dto.event.message.AnyMessageEvent
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
@Shiro
@Component
class WfTranslateLexicon {
    @Autowired
    lateinit var wfLexiconService: WfLexiconService

    @Autowired
    lateinit var wfRivenService: WfRivenService

    /**
     * 获取Json数据并进行格式化
     *
     * @param lexiconMap 词库Map
     * @return 格式化后的词库List
     */
    fun getLexiconList(lexiconMap: MutableMap<String, WfLexiconEntity>): MutableList<WfLexiconEntity> {
        updateLexicon(lexiconMap, WARFRAME_MARKET_ITEMS, LANGUAGE_EN_HANS, "items", "item_name")
        updateLexicon(lexiconMap, WARFRAME_MARKET_ITEMS, LANGUAGE_ZH_HANS, "items", "item_name", isChinese = true)
        updateLexicon(lexiconMap, WARFRAME_MARKET_LOCATION, LANGUAGE_EN_HANS, "locations", "system_name")
        updateLexicon(
            lexiconMap,
            WARFRAME_MARKET_LOCATION,
            LANGUAGE_ZH_HANS,
            "locations",
            "system_name",
            isChinese = true
        )

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

    private fun updateLexicon(
        lexiconMap: MutableMap<String, WfLexiconEntity>,
        url: String,
        language: MutableMap<String, Any>,
        listKey: String,
        nameKey: String,
        isChinese: Boolean = false
    ) {
        val items = HttpUtil.doGetJson(url = url, headers = language)["payload"][listKey]
        items.forEach { item ->
            val id = item["id"].textValue()
            val entity = lexiconMap[id] ?: WfLexiconEntity(
                id = id,
                enItemName = if (!isChinese) item[nameKey].textValue() else "",
                urlName = item["url_name"].textValue(),
                zhItemName = if (isChinese) item[nameKey].textValue() else ""
            )
            if (isChinese) {
                entity.zhItemName = item[nameKey].textValue()
            } else {
                entity.enItemName = item[nameKey].textValue()
            }
            lexiconMap[id] = entity
        }
    }

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
                group = item["group"].textValue(),
                enName = if (!isChinese) item[nameKey].textValue() else "",
                urlName = item["url_name"].textValue(),
                zhName = if (isChinese) item[nameKey].textValue() else ""
            )
            if (isChinese) {
                entity.zhName = item[nameKey].textValue()
            } else {
                entity.enName = item[nameKey].textValue()
            }
            rivenMap[id] = entity
        }
    }


    @OptIn(DelicateCoroutinesApi::class)
    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "更新词库")
    fun upDataWfTranslateLexicon(bot: Bot, event: AnyMessageEvent) {
        val lexiconMap: MutableMap<String, WfLexiconEntity> = mutableMapOf()
        val rivenMap: MutableMap<String, WfRivenEntity> = mutableMapOf()

        GlobalScope.launch {
            try {
                // 获取中英文JSON数据并解析
                bot.sendMsg(event, "词库更新中，请稍等", false)

                // 使用async并行执行插入操作
                val lexiconJob = async { wfLexiconService.insertLexicon(getLexiconList(lexiconMap)) }
                val rivenJob = async { wfRivenService.insertRiven(getRivenList(rivenMap)) }

                // 等待所有任务完成
                lexiconJob.await()
                rivenJob.await()

                bot.sendMsg(event, "词库更新完成", false)
            } finally {
                // 显式地将变量置空
                lexiconMap.clear()
                System.gc()
            }
        }
    }
}