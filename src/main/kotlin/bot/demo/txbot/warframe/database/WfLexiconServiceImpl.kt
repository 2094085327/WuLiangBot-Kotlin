package bot.demo.txbot.warframe.database

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import kotlin.math.ln


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

    /**
     * 批量插入词库
     *
     * @param wfEnLexiconList 待插入的数据列表
     */
    override fun insertLexicon(wfEnLexiconList: List<WfLexiconEntity>) {
        lexiconMapper.insertOrUpdateBatch(wfEnLexiconList)
    }

    override fun turnKeyToUrlNameByLexicon(zh: String): WfLexiconEntity? {
        val queryWrapper =
            QueryWrapper<WfLexiconEntity>()
                .eq("in_market", 1)
                .eq("zh_item_name", zh)
                .or()
                .eq("en_item_name", zh)
                .eq("in_market", 1)
        val wfLexiconEntity = lexiconMapper.selectList(queryWrapper).firstOrNull()

        return if (wfLexiconEntity != null) {
            wfLexiconEntity.useCount = wfLexiconEntity.useCount?.plus(1)
            lexiconMapper.updateById(wfLexiconEntity)
            return wfLexiconEntity
        } else null
    }

    override fun turnKeyToUrlNameByLexiconLike(zh: String): List<WfLexiconEntity?>? {
        // 移除关键词中的"蓝图"字样以优化匹配
        val cleanZh = zh.replace("总图", "蓝图").replace("蓝图", "")

        // 生成并排序子字符串，优先处理较长的片段
        val sortedSubstrings = generateSubstrings(cleanZh).sortedByDescending { it.length }

        // 使用集合记录已检查过的子串，避免重复工作
        val cache = mutableSetOf<String>()
        var newEnName: String? = null
        var remainingString = cleanZh // 剩余未匹配的部分

        for (substring in sortedSubstrings) {
            if (cache.add(substring)) {// 防止重复处理并检查匹配
                val en = lexiconMapper.selectEnFromOther(key = substring).firstOrNull()
                if (en != null) {
                    newEnName = en
                    remainingString = cleanZh.replaceFirst(substring, "", ignoreCase = true)
                    break // 匹配成功则跳出循环
                }

                val selectName = lexiconMapper.selectByZhItemName(substring)
                if (selectName != null) {
                    newEnName = selectName
                    remainingString = zh.replaceFirst(substring, "", ignoreCase = true)
                    break // 匹配成功则跳出循环
                }
            }
        }

        // 构建最终的查询字符串，结合已匹配的英文名和剩余的中文部分
        val addString = when {
            remainingString.isEmpty() && !zh.contains("蓝图") && !zh.contains("总图") -> "一套"
            zh.contains("总图") || zh.contains("蓝图") -> "蓝图"
            else -> remainingString
        }

        val finalQueryString =
            newEnName?.plus(addString) ?: (cleanZh + if (zh.contains("蓝图") || zh.contains("总图")) "蓝图" else "")

        // 构造查询条件
        val queryWrapper = QueryWrapper<WfLexiconEntity>().apply {
            eq("in_market", 1)
            like("url_name", "%${finalQueryString.replace(" ", "%_%")}%")
            or()
            eq("in_market", 1)
            apply("zh_item_name REGEXP {0}", finalQueryString.replace("", ".*").drop(2).dropLast(2))
            or()
            eq("in_market", 1)
            like("en_item_name", "%${finalQueryString.replace(" ", "%")}%")
            or()
            eq("in_market", 1)
            apply("zh_item_name REGEXP {0}", zh.replace("", ".*").drop(2).dropLast(2))
            or()
            eq("in_market", 1)
            like("en_item_name", "%${zh.replace(" ", "%")}%")
            or()
            eq("in_market", 1)
            like("url_name", "%${zh.replace(" ", "%_%")}%")
        }

        // 获取查询结果
        val resultList = lexiconMapper.selectList(queryWrapper)

        // 找到最大 useCount 用于归一化
        val maxUseCount = resultList.maxOfOrNull { it?.useCount ?: 0 }?.toDouble() ?: 1.0


        val coefficients = resultList.map {
            it?.let { item ->
                val itemName = item.zhItemName ?: item.enItemName!!
                sorensenDiceCoefficient(finalQueryString, itemName)
            } ?: 0.0
        }
        // 检查所有finalQueryCoefficient是否相等
        val areAllCoefficientsEqual = coefficients.distinct().size == 1

        val sortedResultList = resultList.sortedByDescending { item ->
            item?.let {
                val itemName = it.zhItemName ?: it.enItemName!!
                if (itemName.contains("赋能·充沛")) {
                    return@sortedByDescending Double.MAX_VALUE
                }
                val finalQueryCoefficient = sorensenDiceCoefficient(finalQueryString, itemName)
                val weightedUseCount = ln(it.useCount!!.toDouble() + 1) / ln(maxUseCount + 1)
                val coefficientSum = 0.8 * finalQueryCoefficient + 0.1 * weightedUseCount * 2

                val specialBonus = when {
                    areAllCoefficientsEqual && itemName.contains("蓝图") && Regex("[^蓝图]*蓝图[^蓝图]*").matches(
                        finalQueryString
                    ) -> 1.5 + coefficientSum

                    else -> 0.0
                }
                val setBonus = if (itemName.contains("一套")) specialBonus + 2.0 else 0.0

                0.8 * finalQueryCoefficient + 0.1 * specialBonus + 0.1 * setBonus
            } ?: 0.0
        }


        val updateEntity = sortedResultList.firstOrNull()
        updateEntity?.let {
            it.useCount = it.useCount?.plus(1)
            lexiconMapper.updateById(it)
        }
        return sortedResultList
    }

    override fun fuzzyQuery(key: String): List<WfLexiconEntity?>? {
        // 构造正则表达式用于模糊查询
        val regex = key.replace("", ".*").drop(2).dropLast(2)
        val queryWrapper = QueryWrapper<WfLexiconEntity>()
            .eq("in_market", 1)
            .apply("zh_item_name REGEXP {0}", regex)
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