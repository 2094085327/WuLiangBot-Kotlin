package bot.demo.txbot.warframe.database

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
class WfLexiconServiceImpl @Autowired constructor(
    private val lexiconMapper: WfLexiconMapper
) : ServiceImpl<WfLexiconMapper?, WfLexiconEntity?>(), WfLexiconService {

    /**
     * 批量插入词库
     *
     * @param wfEnLexiconList 待插入的数据列表
     */
    override fun insertLexicon(wfEnLexiconList: List<WfLexiconEntity>) {
        lexiconMapper.insertOrUpdateBatch(wfEnLexiconList)
    }

    override fun getOtherName(zh: String): String? {
        return lexiconMapper.selectByZhItemName(zh)
    }

    override fun getOtherEnName(en: String): String? {
        return lexiconMapper.selectByEnItemName(en)?.firstOrNull()
    }

    override fun getZhName(key: String): String? {
        val queryWrapper = QueryWrapper<WfLexiconEntity>().eq("en_item_name", key)
        return lexiconMapper.selectList(queryWrapper)?.firstOrNull()?.zhItemName
    }

    override fun insertOtherName(enName: String, zhName: String) {
        lexiconMapper.insertNewOtherName(enName, zhName)
    }

    override fun selectAllOtherName(): List<WfOtherNameEntity> {
        return lexiconMapper.selectAllOtherName()
    }

    override fun deleteOtherName(id: Int) {
        lexiconMapper.deleteOtherNameById(id)
    }

    override fun updateOtherName(id: Int, otherName: String) {
        lexiconMapper.updateOtherNameById(id, otherName)
    }
}