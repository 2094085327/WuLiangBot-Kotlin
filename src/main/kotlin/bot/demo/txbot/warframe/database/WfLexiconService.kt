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

    /**
     * 进行超模糊查询
     *
     * @param key 关键字
     * @return 查询结果
     */
    fun fuzzyQuery(key: String): List<WfLexiconEntity?>?

    /**
     * 获取其他名称
     *
     * @param zh 中文
     * @return 英文
     */
    fun getOtherName(zh: String): String?

    /**
     * 获取其他名称
     *
     * @param en 英文
     * @return 中文别名
     */
    fun getOtherEnName(en: String): String?

    /**
     * 从词库获取中文名称
     *
     * @param key 输入的关键字
     * @return 英文名称
     */
    fun getZhName(key: String): String?
}