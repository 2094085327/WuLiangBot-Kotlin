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

    override fun insertLexicon(wfEnLexiconList: List<WfLexiconEntity>) {
        wfEnLexiconList.forEach { enLexicon ->
            lexiconMapper.insertIgnore(enLexicon)
        }
    }

    override fun turnKeyToUrlNameByLexicon(zh: String): WfLexiconEntity? {
        val queryWrapper = QueryWrapper<WfLexiconEntity>().eq("zh_item_name", zh).or().eq("en_item_name", zh)
        return lexiconMapper.selectOne(queryWrapper)
    }

    private fun generateSubstrings(input: String): List<String> {
        return input.indices.flatMap { i -> (i + 1..input.length).map { j -> input.substring(i, j) } }
    }

    override fun turnKeyToUrlNameByLexiconLike(zh: String): List<WfLexiconEntity?>? {
        val cleanZh = if (zh.contains("蓝图")) zh.replace("蓝图", "") else zh
        val sortedSubstrings = generateSubstrings(cleanZh).sortedByDescending { it.length }

        val cache = mutableSetOf<String>()
        var newEnName: String? = null
        var remainingString = zh

        for (substring in sortedSubstrings) {
            if (cache.add(substring)) {
                val enItemName = lexiconMapper.selectByZhItemName(substring)
                if (enItemName != null) {
                    newEnName = enItemName
                    remainingString = zh.replaceFirst(substring, "", ignoreCase = true)
                    break
                }
            }
        }

        val finalQueryString = newEnName?.plus(remainingString) ?: zh
        val regex = finalQueryString.replace("", ".*").drop(2).dropLast(2)
        val queryWrapper = QueryWrapper<WfLexiconEntity>()
            .like("url_name", "%${finalQueryString.replace(" ", "%_%")}%")
            .or()
            .apply("zh_item_name REGEXP {0}", regex)
            .or()
            .like("en_item_name", "%${finalQueryString.replace(" ", "%")}%")

        return lexiconMapper.selectList(queryWrapper).sortedBy { it?.urlName?.split("_")?.size }
    }


    override fun fuzzyQuery(key: String): List<WfLexiconEntity?>? {
        val queryWrapper = QueryWrapper<WfLexiconEntity>()
            .like("zh_item_name", "%$key%")
            .or()
            .like("en_item_name", "%$key%")
        return lexiconMapper.selectList(queryWrapper)
    }

    override fun getOtherName(zh: String): String? {
        return lexiconMapper.selectByZhItemName(zh)
    }

    override fun getOtherEnName(en: String): String? {
        return lexiconMapper.selectByEnItemName(en)?.firstOrNull()
    }

}