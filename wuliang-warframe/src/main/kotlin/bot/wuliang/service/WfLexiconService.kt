package bot.wuliang.service

import bot.wuliang.entity.WfLexiconEntity
import bot.wuliang.entity.WfOtherNameEntity
import com.baomidou.mybatisplus.extension.service.IService

interface WfLexiconService : IService<WfLexiconEntity?> {
    /**
     * 插入词库
     *
     * @param wfEnLexiconList 词库List
     */
    fun insertLexicon(wfEnLexiconList: List<WfLexiconEntity>)

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

    /**
     * 插入别名
     *
     * @param enName 英文名
     * @param zhName 中文别名
     */
    fun insertOtherName(enName: String, zhName: String)

    /**
     * 查询全部的别名
     *
     */
    fun selectAllOtherName(): List<WfOtherNameEntity>

    /**
     * 根据ID删除别名
     *
     * @param id 别名Id
     */
    fun deleteOtherName(id: Int)

    /**
     * 根据ID更新别名
     *
     * @param id 别名Id
     * @param otherName 别名
     */
    fun updateOtherName(id: Int, otherName: String)
}