package bot.wuliang.service.impl

import bot.wuliang.entity.WfRivenEntity
import bot.wuliang.mapper.WfRivenMapper
import bot.wuliang.service.WfRivenService
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
class WfRivenServiceImpl : ServiceImpl<WfRivenMapper?, WfRivenEntity?>(), WfRivenService {
    @Autowired
    lateinit var rivenMapper: WfRivenMapper

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
     * 插入或更新玄骸词库
     *
     * @param wfEnRivenList
     */
    override fun insertRiven(wfEnRivenList: List<WfRivenEntity>) {
        rivenMapper.insertOrUpdateBatch(wfEnRivenList)
    }

    override fun turnKeyToUrlNameByRiven(zh: String): WfRivenEntity? {
        val queryWrapper = QueryWrapper<WfRivenEntity>()
            .eq("zh", zh)
            .eq("attributes", 0)
            .or()
            .eq("en", zh)
            .eq("attributes", 0)
        return rivenMapper.selectOne(queryWrapper)
    }

    override fun turnKeyToUrlNameByLich(zh: String): WfRivenEntity? {
        val queryWrapper = QueryWrapper<WfRivenEntity>()
            .eq("zh", zh)
            .eq("attributes", 2)
            .or()
            .eq("en", zh)
            .eq("attributes", 2)
        return rivenMapper.selectOne(queryWrapper)
    }

    override fun turnKeyToUrlNameByRivenLike(zh: String): List<WfRivenEntity?>? {
        val regex = zh.toCharArray().joinToString(".*") { it.toString() }  // 将输入字符串转换为.*分隔的正则表达式
        val queryWrapper = QueryWrapper<WfRivenEntity>()
            .apply("zh REGEXP {0}", regex)
            .eq("attributes", 1)
            .or()
            .like("en", "%$zh%")
            .eq("attributes", 1)
        return rivenMapper.selectList(queryWrapper)
    }

    override fun turnUrlNameToKeyByRiven(urlName: String): String {
        val queryWrapper = QueryWrapper<WfRivenEntity>().eq("url_name", urlName)
        return rivenMapper.selectOne(queryWrapper)?.zhName ?: ""
    }

    override fun superFuzzyQuery(key: String): List<WfRivenEntity?>? {
        val queryWrapper = QueryWrapper<WfRivenEntity>()
            .like("zh", "%$key%")
            .or()
            .like("en", "%$key%")
        return rivenMapper.selectList(queryWrapper)
    }

    fun getWfRivenEntityLike(queryWrapper: QueryWrapper<WfRivenEntity>, key: String): List<WfRivenEntity?> {
        // 获取查询结果
        val resultList = rivenMapper.selectList(queryWrapper)

        // 对查询结果按 Sorensen-Dice 系数排序，然后按 id 排序
        return resultList.distinctBy { it?.id }.sortedWith(compareByDescending<WfRivenEntity?> {
            sorensenDiceCoefficient(key, it?.zhName ?: it?.enName!!)
        }.thenByDescending { it?.id })
    }

    /**
     * 判断字符串是否为纯字母
     *
     */
    fun String.onlyLetters() = all { it.isLetter() }


    override fun turnKeyToUrlNameByLichLike(key: String): List<WfRivenEntity?> {
        // 构造正则表达式用于模糊查询
        val regex = if (!key.onlyLetters()) key.replace("", ".*").drop(2).dropLast(2) else key

        // 创建查询条件，结合市场状态、URL名称模糊匹配、正则匹配及英文名模糊匹配
        val queryWrapper = QueryWrapper<WfRivenEntity>()
            .eq("attributes", 2)
            .like("url_name", "%${key.replace(" ", "%_%")}%")
            .or()
            .eq("attributes", 2)
            .apply("zh REGEXP {0}", regex)
            .or()
            .eq("attributes", 2)
            .like("en", "%${key.replace(" ", "%")}%")

        return getWfRivenEntityLike(queryWrapper, key)
    }

    override fun searchByRivenLike(key: String): List<WfRivenEntity?> {
        // 构造正则表达式用于模糊查询
        val regex = if (!key.onlyLetters()) key.replace("", ".*").drop(2).dropLast(2) else key

        val queryWrapper = QueryWrapper<WfRivenEntity>()
            .nested {
                it.eq("attributes", 0)
                    .or()
                    .eq("attributes", 2)
            }
            .and {
                it.like("url_name", "%${key.replace(" ", "%_%")}%")
                    .or()
                    .apply("zh REGEXP {0}", regex)
                    .or()
                    .like("en", "%${key.replace(" ", "%")}%")
            }
        return getWfRivenEntityLike(queryWrapper, key)
    }


    override fun selectAllRivenData(): List<WfRivenEntity> {
        return rivenMapper.selectAllRiven()
    }

}