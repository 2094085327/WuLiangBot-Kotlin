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
     * Sorensen-Dice 系数计算字符串相似度
     *
     * @param s 字符串1
     * @param t 字符串2
     * @return 计算后的系数
     */
    fun sorensenDiceCoefficient(s: String, t: String): Double {
        val sBigrams = s.windowed(2, 1).toSet()
        val tBigrams = t.windowed(2, 1).toSet()
        val intersection = sBigrams.intersect(tBigrams).size.toDouble()
        return (2 * intersection) / (sBigrams.size + tBigrams.size)
    }

    /**
     * 根据原字符串生成子字符串
     *
     * @param input 原字符串
     * @return 生成的子字符串列表
     */
    private fun generateSubstrings(input: String): List<String> {
        return input.indices.flatMap { i -> (i + 1..input.length).map { j -> input.substring(i, j) } }
    }

    override fun insertLexicon(wfEnLexiconList: List<WfLexiconEntity>) {
        wfEnLexiconList.forEach { enLexicon ->
            lexiconMapper.insertIgnore(enLexicon)
        }
    }

    override fun turnKeyToUrlNameByLexicon(zh: String): WfLexiconEntity? {
        val queryWrapper =
            QueryWrapper<WfLexiconEntity>()
                .eq("in_market", 1)
                .eq("zh_item_name", zh)
                .or()
                .eq("en_item_name", zh)
                .eq("in_market", 1)
        return lexiconMapper.selectList(queryWrapper).firstOrNull()
    }

    override fun turnKeyToUrlNameByLexiconLike(zh: String): List<WfLexiconEntity?>? {
        // 移除关键词中的"蓝图"字样以优化匹配
        val cleanZh = if (zh.contains("蓝图")) zh.replace("蓝图", "") else zh

        // 生成并排序子字符串，优先处理较长的片段
        val sortedSubstrings = generateSubstrings(cleanZh).sortedByDescending { it.length }

        // 使用集合记录已检查过的子串，避免重复工作
        val cache = mutableSetOf<String>()
        var newEnName: String? = null
        var remainingString = zh // 剩余未匹配的部分

        for (substring in sortedSubstrings) {
            if (cache.add(substring)) { // 防止重复处理
                val enItemName = lexiconMapper.selectByZhItemName(substring)
                if (enItemName != null) {
                    newEnName = enItemName
                    remainingString = zh.replaceFirst(substring, "", ignoreCase = true)
                    break // 匹配成功则跳出循环
                }
            }
        }

        // 构建最终的查询字符串，结合已匹配的英文名和剩余的中文部分
        val finalQueryString = newEnName?.plus(remainingString) ?: zh

        // 构造正则表达式用于模糊查询
        val regex = finalQueryString.replace("", ".*").drop(2).dropLast(2)

        // 创建查询条件，结合市场状态、URL名称模糊匹配、正则匹配及英文名模糊匹配
        val queryWrapper = QueryWrapper<WfLexiconEntity>()
            .eq("in_market", 1)
            .like("url_name", "%${finalQueryString.replace(" ", "%_%")}%")
            .or()
            .eq("in_market", 1)
            .apply("zh_item_name REGEXP {0}", regex)
            .or()
            .eq("in_market", 1)
            .like("en_item_name", "%${finalQueryString.replace(" ", "%")}%")

        // 获取查询结果
        val resultList = lexiconMapper.selectList(queryWrapper)

        // 对查询结果按 Sorensen-Dice 系数排序，然后按 id 排序
        val sortedResultList = resultList.distinctBy { it?.id }.sortedWith(compareByDescending<WfLexiconEntity?> {
            sorensenDiceCoefficient(finalQueryString, it?.zhItemName ?: it?.enItemName!!)
        }.thenByDescending { it?.id })

        // 执行查询并按URL名称分段数量排序结果
        return sortedResultList
    }


    override fun fuzzyQuery(key: String): List<WfLexiconEntity?>? {
        val queryWrapper = QueryWrapper<WfLexiconEntity>()
            .eq("in_market", 1)
            .like("zh_item_name", "%$key%")
            .or()
            .eq("in_market", 1)
            .like("en_item_name", "%$key%")
        return lexiconMapper.selectList(queryWrapper)
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

}