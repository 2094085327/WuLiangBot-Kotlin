package bot.wuliang.service.impl

import bot.wuliang.config.WfMarketConfig.REPLACE_LIST
import bot.wuliang.config.WfMarketConfig.SPECIAL_ITEMS_LIST
import bot.wuliang.entity.WfMarketItemEntity
import bot.wuliang.entity.WfOtherNameEntity
import bot.wuliang.mapper.WfMarketItemMapper
import bot.wuliang.service.WfMarketItemService
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import kotlin.math.ln


/**
 * @description: WfMarketItem的实现类
 * @author Nature Zero
 * @date 2025/1/5 13:28
 */
@Service
class WfMarketItemServiceImpl : ServiceImpl<WfMarketItemMapper?, WfMarketItemEntity?>(), WfMarketItemService {
    @Autowired
    private lateinit var wfMarketItemMapper: WfMarketItemMapper

    /**
     * 根据原字符串生成子字符串
     *
     * @param input 原字符串
     * @param minLength 最小长度
     * @return 生成的子字符串列表
     */
    private fun generateSubstrings(input: String, minLength: Int): List<String> {
        return input.indices.flatMap { i ->
            (i + 1..input.length).map { j -> input.substring(i, j) }
        }.filter { substring ->
            // 保留长度大于 minLength，或包含非 a-zA-Z 字母的字符
            substring.length > minLength || substring.any { !it.toString().matches(Regex("[a-zA-Z]")) }
        }
    }


    /**
     * Sorensen-Dice 系数计算字符串相似度
     *
     * @param s 字符串1
     * @param t 字符串2
     * @return 计算后的系数
     */
    private fun sorensenDiceCoefficient(s: String, t: String): Double {
        val sBigrams = s.windowed(2, 1).toSet()
        val tBigrams = t.windowed(2, 1).toSet()
        val intersection = sBigrams.intersect(tBigrams).size.toDouble()
        return (2 * intersection) / (sBigrams.size + tBigrams.size)
    }

    /**
     * 对别名进行排序
     *
     * @param queryResults 查询结果列表
     * @param target 目标字符串
     * @return 排序后的结果
     */
    fun sortResultsBySimilarity(queryResults: List<WfOtherNameEntity>, target: String): List<WfOtherNameEntity> {
        return queryResults.sortedByDescending { result ->
            sorensenDiceCoefficient(result.otherName!!, target)
        }
    }


    /**
     * 权重计算
     *
     * @param item 物品信息
     * @param finalQueryString 最终查询字符串
     * @param maxUseCount 最大使用次数
     * @return 计算后的权重
     */
    private fun calculateWeight(
        item: WfMarketItemEntity,
        finalQueryString: String,
        maxUseCount: Double,
        areAllCoefficientsEqual: Boolean
    ): Double {
        val itemName = item.zhName ?: item.enName!!
        val finalQueryCoefficient = sorensenDiceCoefficient(finalQueryString, itemName)
        val weightedUseCount = ln(item.useCount!!.toDouble() + 1) / ln(maxUseCount + 1)

        // 特殊权重
        val specialBonus = when {
            areAllCoefficientsEqual && itemName.contains("蓝图") &&
                    Regex("[^蓝图]*蓝图[^蓝图]*").matches(finalQueryString) -> 1.5

            else -> 0.0
        }
        val setBonus = if (itemName.contains("一套")) 2.0 else 0.0

        // 综合权重计算
        return 0.8 * finalQueryCoefficient + 0.1 * weightedUseCount + 0.1 * (specialBonus + setBonus)
    }


    override fun updateMarketItem(marketItemList: MutableList<WfMarketItemEntity>) {
        // 每批插入1000条
        val batchSize = 1000
        var i = 0
        while (i < marketItemList.size) {
            val batch: MutableList<WfMarketItemEntity> =
                marketItemList.subList(i, marketItemList.size.coerceAtMost(i + batchSize))
            wfMarketItemMapper.insertMarketItem(batch)
            i += batchSize
        }
    }

    override fun selectItemByAccurateNature(key: String): WfMarketItemEntity? {
        val wfMarketItemEntity = wfMarketItemMapper.getUrlNameFromKey(key)

        return if (wfMarketItemEntity != null) {
            wfMarketItemEntity.useCount = wfMarketItemEntity.useCount?.plus(1)
            wfMarketItemMapper.updateById(wfMarketItemEntity)
            return wfMarketItemEntity
        } else null
    }

    override fun getItemByFuzzyMatching(key: String): List<WfMarketItemEntity>? {
        // 移除关键词中的"蓝图"字样以优化匹配
        val cleanZh = key.replace("总图", "蓝图").replace("蓝图", "")

        // 生成并排序子字符串，优先处理较长的片段
        val sortedSubstrings = generateSubstrings(cleanZh, 1).sortedByDescending { it.length }

        // 使用集合记录已检查过的子串，避免重复工作
        val cache = mutableSetOf<String>()
        var newEnName: String? = null
        var remainingString = cleanZh // 剩余未匹配的部分

        // 对子字符串执行批量查询
        val queryResults = wfMarketItemMapper.batchSelectBySubstrings(sortedSubstrings)
        val sortedResults = sortResultsBySimilarity(queryResults, cleanZh)


        // 遍历子字符串，进行别名匹配
        sortedSubstrings.firstOrNull { substring ->
            cache.add(substring) && sortedResults.any { result ->
                val match =
                    listOfNotNull(result.enItemName, result.otherName).find { cleanZh.contains(it, ignoreCase = true) }
                if (match != null) {
                    newEnName = result.enItemName // 始终使用 result.enItemName
                    remainingString = cleanZh.replaceFirst(match, "", ignoreCase = true).trim() // 替换匹配到的部分
                    true
                } else false
            }
        } != null


        // 构建最终的查询字符串，结合已匹配的英文名和剩余的中文部分
        val replaceBoolean = REPLACE_LIST.any { key.contains(it) }

        // 根据剩余字符串的内容决定是否需要添加"一套"或"蓝图"
        val addString = when {
            remainingString.isEmpty() && !replaceBoolean -> "一套"
            replaceBoolean -> "蓝图"
            else -> remainingString
        }

        val finalQueryString = newEnName?.plus(addString) ?: (cleanZh + if (replaceBoolean) "蓝图" else "")

        val paramsMap = mapOf(
            "finalQueryString" to finalQueryString.replace(" ", "%_%"),
            "finalQueryStringRegex" to finalQueryString.replace("", ".*").drop(2).dropLast(2),
            "zhRegex" to key.replace("", ".*").drop(2).dropLast(2),
            "zh" to key.replace(" ", "%_%")
        )
        // 获取查询结果
        val resultList = wfMarketItemMapper.selectItemByFuzzyMatching(paramsMap)
        if (resultList.isNullOrEmpty()) return null

        // 找到最大 useCount 用于归一化 最大值为0时取1避免除零
        val maxUseCount = resultList.maxOfOrNull { it.useCount ?: 0 }
            ?.takeIf { it > 0 }
            ?.toDouble() ?: 1.0


        val coefficients = resultList.map {
            it.let { item ->
                val itemName = item.zhName ?: item.enName!!
                sorensenDiceCoefficient(finalQueryString, itemName)
            }
        }
        // 检查所有finalQueryCoefficient是否相等
        val areAllCoefficientsEqual = coefficients.distinct().size == 1

        // 根据权重排序
        val sortedResultList = resultList.sortedByDescending { item ->
            val containsAnyKeyword = SPECIAL_ITEMS_LIST.any {
                item.zhName?.contains(it) == true || item.enName?.contains(it) == true
            }

            if (containsAnyKeyword) Double.MAX_VALUE // 特殊项优先
            else calculateWeight(item, finalQueryString, maxUseCount, areAllCoefficientsEqual)
        }


        val updateEntity = sortedResultList.first()

        updateEntity.let {
            it.useCount = it.useCount?.plus(1)
            wfMarketItemMapper.updateById(it)
        }
        return sortedResultList
    }

    override fun fuzzyQuery(key: String): List<WfMarketItemEntity?> {
        // 构造正则表达式用于模糊查询
        val regex = key.replace("", ".*").drop(2).dropLast(2)
        val queryWrapper = QueryWrapper<WfMarketItemEntity>()
            .apply("zh_name REGEXP {0}", regex)
            .or()
            .like("en_name", "%$key%")
        return wfMarketItemMapper.selectList(queryWrapper)
    }


}