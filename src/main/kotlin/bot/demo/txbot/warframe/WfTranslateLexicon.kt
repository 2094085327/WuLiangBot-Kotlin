package bot.demo.txbot.warframe

import bot.demo.txbot.common.utils.HttpUtil
import bot.demo.txbot.warframe.database.WfLexiconEntity
import bot.demo.txbot.warframe.database.WfLexiconService
import com.mikuac.shiro.annotation.AnyMessageHandler
import com.mikuac.shiro.annotation.MessageHandlerFilter
import com.mikuac.shiro.annotation.common.Shiro
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

    /**
     * 获取Json数据并进行格式化
     *
     * @param lexiconMap 词库Map
     * @return 格式化后的词库List
     */
    fun getLexiconList(lexiconMap: MutableMap<String, WfLexiconEntity>): MutableList<WfLexiconEntity> {
        val enItems = HttpUtil.doGetJson(WARFRAME_MARKET_ITEMS, LANGUAGE_EN_HANS)["payload"]["items"]
        enItems.forEach { item ->
            val id = item["id"].textValue()
            lexiconMap[id] = WfLexiconEntity(
                id = id,
                enItemName = item["item_name"].textValue(),
                urlName = item["url_name"].textValue(),
                zhItemName = "" // 初始为空，稍后更新
            )
        }

        // 获取中文JSON数据并解析
        val zhItems = HttpUtil.doGetJson(WARFRAME_MARKET_ITEMS, LANGUAGE_ZH_HANS)["payload"]["items"]
        zhItems.forEach { item ->
            val id = item["id"].textValue()
            lexiconMap[id]?.apply {
                zhItemName = item["item_name"].textValue()
            }
        }

        // 转换Map为List
        return lexiconMap.values.toMutableList()
    }


    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "更新词库")
    fun upDataWfTranslateLexicon() {
        val lexiconMap: MutableMap<String, WfLexiconEntity> = mutableMapOf()

        try {
            // 获取英文JSON数据并解析
            val lexiconList = getLexiconList(lexiconMap)

            // 更新数据库
            wfLexiconService.setEnLexicon(lexiconList)
        } finally {
            // 显式地将变量置空
            lexiconMap.clear()
            System.gc()
        }

    }
}