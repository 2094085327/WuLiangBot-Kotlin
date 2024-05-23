package bot.demo.txbot.warframe.database

import com.baomidou.mybatisplus.extension.service.IService

interface WfRivenService : IService<WfRivenEntity?> {
    /**
     * 插入词库
     *
     * @param wfEnRivenList 词库List
     */
    fun insertRiven(wfEnRivenList: List<WfRivenEntity>)

    /**
     * 通过词库转换中文为英文
     *
     * @param zh 中文物品
     * @return 英文物品
     */
    fun turnKeyToUrlNameByRiven(zh: String): WfRivenEntity?

    /**
     * 通过词库转换中文为英文
     *
     * @param zh 中文物品
     * @return 英文物品
     */
    fun turnKeyToUrlNameByRivenLike(zh: String): List<WfRivenEntity?>?

    /**
     * 通过词库转换url_name为中文
     *
     * @param urlName url_name
     * @return 中文物品
     */
    fun turnUrlNameToKeyByRiven(urlName: String): String

    /**
     * 超模糊查询
     *
     * @param key 关键字
     * @return 查询结果
     */
    fun superFuzzyQuery(key: String): List<WfRivenEntity?>?
}