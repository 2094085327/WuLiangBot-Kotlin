package bot.wuliang.mapper

import bot.wuliang.entity.WfMarketItemEntity
import bot.wuliang.entity.WfOtherNameEntity
import com.baomidou.mybatisplus.core.mapper.BaseMapper
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import org.apache.ibatis.annotations.Select


/**
 * @description: 针对表【wf_market_item】的数据库操作Mapper
 * @author Nature Zero
 * @date 2025/1/5 13:25
 */
@Mapper
interface WfMarketItemMapper : BaseMapper<WfMarketItemEntity?> {
    /**
     * 更新/插入物品信息
     *
     * @param marketItemList 物品信息列表
     */
    fun insertMarketItem(marketItemList: MutableList<WfMarketItemEntity>)

    /**
     * 根据关键字获取物品信息
     *
     * @param key 关键字
     * @return 物品信息
     */
    @Select("select id,zh_name,en_name,url_name,use_count from wf_market_item where (zh_name = #{key} or en_name = #{key})")
    fun getUrlNameFromKey(@Param("key") key: String): WfMarketItemEntity?

    /**
     * 根据关键字列表批量查询
     *
     * @param sortedSubstrings 排序后的关键字列表
     * @return 查询结果
     */
    fun batchSelectBySubstrings(sortedSubstrings: List<String>): List<WfOtherNameEntity>

    /**
     * 根据关键字使用模糊查询获取物品信息
     *
     * @param paramsMap 查询参数
     * @return 查询结果
     */
    fun selectItemByFuzzyMatching(@Param("paramsMap")paramsMap: Map<String, String>): List<WfMarketItemEntity>?
}