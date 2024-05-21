package bot.demo.txbot.warframe.database

import com.baomidou.mybatisplus.extension.service.IService

interface WfLexiconService : IService<WfLexiconEntity?> {
    /**
     * 插入词库
     *
     * @param wfEnLexiconList 词库List
     */
    fun insertLexicon(wfEnLexiconList: List<WfLexiconEntity>)

    /**
     * 通过词库转换中文为英文
     *
     * @param zh 中文物品
     * @return 英文物品
     */
    fun turnKeyToUrlNameByLexicon(zh: String): WfLexiconEntity?

    /**
     * 模糊匹配词库转换中文为英文
     *
     * @param zh 中文物品
     * @return 英文物品
     */
    fun turnKeyToUrlNameByLexiconLike(zh: String): List<WfLexiconEntity?>?
}