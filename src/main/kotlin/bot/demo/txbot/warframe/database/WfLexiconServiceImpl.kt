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
class WfLexiconServiceImpl @Autowired constructor(
    private val lexiconMapper: WfLexiconMapper
) : ServiceImpl<WfLexiconMapper?, WfLexiconEntity?>(), WfLexiconService {

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
        // 拆分输入字符串，生成所有子字符串，并按长度从长到短排序
        val sortedSubstrings = generateSubstrings(zh).sortedByDescending { it.length }

        // 查找第一个匹配的别名，并替换
        var newEnName: String? = null
        var remainingString = zh

        // 使用HashSet来缓存查询结果，避免多次查询相同的子字符串
        val cache = mutableSetOf<String>()

        for (substring in sortedSubstrings) {
            if (substring in cache) continue
            cache.add(substring)
            val enItemName = lexiconMapper.selectByZhItemName(substring)
            if (enItemName != null) {
                newEnName = enItemName
                remainingString = zh.replaceFirst(substring, "", ignoreCase = true)
                break
            }
        }

        // 构建最终的查询字符串
        val finalQueryString = newEnName?.plus(remainingString) ?: zh

        // 直接构建正则表达式
        val regex = finalQueryString.replace("", ".*").drop(2).dropLast(2)
        val queryWrapper = QueryWrapper<WfLexiconEntity>()
            .apply("zh_item_name REGEXP {0}", regex)
            .or()
            .like("en_item_name", "%$zh%")

        return lexiconMapper.selectList(queryWrapper)
    }

    override fun fuzzyQuery(key: String): List<WfLexiconEntity?>? {
        val queryWrapper = QueryWrapper<WfLexiconEntity>()
            .like("zh_item_name", "%$key%")
            .or()
            .like("en_item_name", "%$key%")
        return lexiconMapper.selectList(queryWrapper)
    }

    private fun generateSubstrings(input: String): List<String> {
        val substrings = mutableListOf<String>()
        for (i in input.indices) {
            for (j in i + 1..input.length) {
                substrings.add(input.substring(i, j))
            }
        }
        return substrings
    }
}