package bot.demo.txbot.warframe.database

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.util.logging.Logger


/**
 * @description: Warframe 词库服务实现类
 * @author Nature Zero
 * @date 2024/5/20 下午11:50
 */
@Service
class WfLexiconServiceImpl : ServiceImpl<WfLexiconMapper?, WfLexiconEntity?>(), WfLexiconService {
    @Autowired
    lateinit var lexiconMapper: WfLexiconMapper

    private val logger: Logger = Logger.getLogger(WfLexiconServiceImpl::class.java.getName())


    override fun insertLexicon(wfEnLexiconList: List<WfLexiconEntity>) {
        wfEnLexiconList.forEach { enLexicon ->
            lexiconMapper.insertIgnore(enLexicon)
        }
        logger.info("词库更新完成")
    }

    override fun turnKeyToUrlNameByLexicon(zh: String): WfLexiconEntity? {
        val queryWrapper = QueryWrapper<WfLexiconEntity>().eq("zh_item_name", zh).or().eq("en_item_name", zh)
        return lexiconMapper.selectOne(queryWrapper)
    }

    override fun turnKeyToUrlNameByLexiconLike(zh: String): List<WfLexiconEntity?>? {
        val regex = zh.toCharArray().joinToString(".*") { it.toString() }  // 将输入字符串转换为.*分隔的正则表达式
        val queryWrapper = QueryWrapper<WfLexiconEntity>()
            .apply("zh_item_name REGEXP {0}", regex)
            .or()
            .like("en_item_name", "%$zh%")
        return lexiconMapper.selectList(queryWrapper)
    }
}