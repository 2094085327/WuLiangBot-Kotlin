package bot.wuliang.service.impl

import bot.wuliang.entity.WfLexiconEntity
import bot.wuliang.entity.WfMarketItemEntity
import bot.wuliang.entity.WfOtherNameEntity
import bot.wuliang.mapper.WfLexiconMapper
import bot.wuliang.service.WfLexiconService
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service


/**
 * @description: Warframe 词库服务实现类
 * @author Nature Zero
 * @date 2024/5/20 下午11:50
 */
@Service
class WfLexiconServiceImpl : ServiceImpl<WfLexiconMapper?, WfLexiconEntity?>(), WfLexiconService {

    @Autowired
    private lateinit var lexiconMapper: WfLexiconMapper

    /**
     * 批量插入词库
     *
     * @param wfEnLexiconList 待插入的数据列表
     */
    override fun insertLexicon(wfEnLexiconList: List<WfLexiconEntity>) {
        lexiconMapper.insertOrUpdateBatch(wfEnLexiconList)
    }

    /**
     * 获取其他名称
     *
     * @param zh 中文
     * @return 英文
     */
    override fun getOtherName(zh: String): String? {
        return lexiconMapper.selectByZhItemName(zh)
    }

    /**
     * 获取其他名称
     *
     * @param en 英文
     * @return 中文别名
     */
    override fun getOtherEnName(en: String): String? {
        return lexiconMapper.selectByEnItemName(en)?.firstOrNull()
    }

    /**
     * 通过英文获取中文名称
     *
     * @param key 输入的关键字
     * @return 中文名称
     */
    override fun getZhName(key: String): String? {
        val queryWrapper = QueryWrapper<WfLexiconEntity>().eq("en_item_name", key)
        return lexiconMapper.selectList(queryWrapper)?.firstOrNull()?.zhItemName
    }

    /**
     * 通过中文获取英文名称
     *
     * @param key 输入的关键字
     * @return 英文名称
     */
    override fun getEnName(key: String): String? {
        val queryWrapper = QueryWrapper<WfLexiconEntity>().eq("zh_item_name", key)
        return lexiconMapper.selectList(queryWrapper)?.firstOrNull()?.enItemName
    }

    /**
     * 插入别名
     *
     * @param enName 英文名
     * @param zhName 中文别名
     */
    override fun insertOtherName(enName: String, zhName: String): Int {
        return lexiconMapper.insertNewOtherName(enName, zhName)
    }

    /**
     * 查询全部的别名
     *
     */
    override fun selectAllOtherName(): List<WfOtherNameEntity> {
        return lexiconMapper.selectAllOtherName()
    }

    /**
     * 根据ID删除别名
     *
     * @param id 别名Id
     */
    override fun deleteOtherName(id: Int) {
        lexiconMapper.deleteOtherNameById(id)
    }

    /**
     * 根据ID更新别名
     *
     * @param id 别名Id
     * @param otherName 别名
     */
    override fun updateOtherName(id: Int, otherName: String) {
        lexiconMapper.updateOtherNameById(id, otherName)
    }

    /**
     * 词库超模糊查询
     *
     * @param key 关键字
     * @return 查询结果
     */
    override fun fuzzyQuery(key: String): List<WfLexiconEntity?> {
        // 构造正则表达式用于模糊查询
        val regex = key.replace("", ".*").drop(2).dropLast(2)
        val queryWrapper = QueryWrapper<WfLexiconEntity>()
            .apply("zh_item_name REGEXP {0}", regex)
            .or()
            .like("en_item_name", "%$key%")
        return lexiconMapper.selectList(queryWrapper)
    }
}