package bot.wuliang.service.impl

import bot.wuliang.entity.WfRivenEntity
import bot.wuliang.mapper.WfRivenMapper
import bot.wuliang.riven.RivenGroups
import bot.wuliang.service.WfRivenService
import bot.wuliang.translation.PublicExportService
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl
import org.springframework.stereotype.Service


/**
 * @description: Warframe 词库服务实现类
 * @author Nature Zero
 * @date 2024/5/20 下午11:50
 */
@Service
class WfRivenServiceImpl(
    private val rivenMapper: WfRivenMapper,
    private val translations: PublicExportService,
) : ServiceImpl<WfRivenMapper?, WfRivenEntity?>(), WfRivenService {

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
     * 按物品标识批量插入或更新武器目录。
     *
     * @param wfEnRivenList 待写入的武器目录记录。
     */
    override fun insertRiven(wfEnRivenList: List<WfRivenEntity>) {
        rivenMapper.insertOrUpdateBatch(wfEnRivenList)
    }

    /** 按中英文名称及翻译候选精确查询标准紫卡武器，排除玄骸和姐妹分组，命中后更新展示译名。 */
    override fun turnKeyToUrlNameByRiven(zh: String): WfRivenEntity? {
        val queryWrapper = QueryWrapper<WfRivenEntity>()
            .nested { it.eq("zh", zh).or().eq("en", zh).or().`in`("en", translations.index().translateQuery(zh) + zh) }
            .notIn("r_group", RivenGroups.LICH, RivenGroups.SISTER)
        return rivenMapper.selectOne(queryWrapper)?.let(translations::localize)
    }

    /** 按中英文名称及翻译候选精确查询玄骸或姐妹武器，命中后更新展示译名。 */
    override fun turnKeyToUrlNameByLich(zh: String): WfRivenEntity? {
        val queryWrapper = QueryWrapper<WfRivenEntity>()
            .nested { it.eq("zh", zh).or().eq("en", zh).or().`in`("en", translations.index().translateQuery(zh) + zh) }
            .`in`("r_group", RivenGroups.LICH, RivenGroups.SISTER)
        return rivenMapper.selectOne(queryWrapper)?.let(translations::localize)
    }

    /** 按中文或英文包含关系查询候选并更新展示译名；空关键词返回空列表。 */
    override fun superFuzzyQuery(key: String): List<WfRivenEntity?>? {
        if (key.isBlank()) return emptyList()
        val queryWrapper = QueryWrapper<WfRivenEntity>()
            .like("zh", key)
            .or()
            .like("en", key)
        return rivenMapper.selectList(queryWrapper).map { it?.let(translations::localize) }
    }

    /** 执行 [queryWrapper] 查询，补齐译名并去重，再按名称相似度及 ID 降序排列。 */
    fun getWfRivenEntityLike(queryWrapper: QueryWrapper<WfRivenEntity>, key: String): List<WfRivenEntity?> {
        // 获取查询结果
        val resultList = rivenMapper.selectList(queryWrapper).map { it?.let(translations::localize) }

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


    /** 在玄骸与姐妹分组中按 slug、中英文名称模糊查询，再按相似度排序。 */
    override fun turnKeyToUrlNameByLichLike(key: String): List<WfRivenEntity?> {
        // 构造正则表达式用于模糊查询
        val regex = if (!key.onlyLetters()) key.replace("", ".*").drop(2).dropLast(2) else key

        // 创建查询条件，结合市场状态、URL名称模糊匹配、正则匹配及英文名模糊匹配
        val queryWrapper = QueryWrapper<WfRivenEntity>()
            .`in`("r_group", RivenGroups.LICH, RivenGroups.SISTER)
            .and {
                it.like("url_name", "%${key.replace(" ", "%_%")}%")
                    .or()
                    .apply("zh REGEXP {0}", regex)
                    .or()
                    .like("en", "%${key.replace(" ", "%")}%")
            }

        return getWfRivenEntityLike(queryWrapper, key)
    }

    /** 按 slug、中英文名称模糊查询武器目录，去重后按名称相似度排序。 */
    override fun searchByRivenLike(key: String): List<WfRivenEntity?> {
        // 构造正则表达式用于模糊查询
        val regex = if (!key.onlyLetters()) key.replace("", ".*").drop(2).dropLast(2) else key

        val queryWrapper = QueryWrapper<WfRivenEntity>()
            .nested {
                it.like("url_name", "%${key.replace(" ", "%_%")}%")
                    .or()
                    .apply("zh REGEXP {0}", regex)
                    .or()
                    .like("en", "%${key.replace(" ", "%")}%")
            }
        return getWfRivenEntityLike(queryWrapper, key)
    }


    /** 读取全部紫卡武器目录，并使用当前词库补齐展示名称。 */
    override fun selectAllRivenData(): List<WfRivenEntity> {
        return rivenMapper.selectAllRiven().map(translations::localize)
    }

}
