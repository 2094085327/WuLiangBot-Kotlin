package bot.wuliang.service

import bot.wuliang.entity.WfMarketItemEntity
import com.baomidou.mybatisplus.extension.service.IService

interface WfMarketItemService : IService<WfMarketItemEntity?> {
    /**
     * 更新/插入物品信息
     *
     * @param marketItemList 物品信息列表
     */
    fun updateMarketItem(marketItemList: List<WfMarketItemEntity>)

    /**
     * 根据关键字获取物品信息
     *
     * @param key 查询关键字
     * @return 查询结果
     */
    fun selectItemByAccurateNature(key: String): WfMarketItemEntity?

    /**
     * 根据中文名称列表查询物品信息
     *
     * @param keyList 中文名称列表
     * @return 查询结果
     */
    fun selectListZhNameList(keyList: List<String>): List<WfMarketItemEntity>?

    /**
     * 模糊匹配词库根据关键字获取物品信息
     *
     * @param key 查询关键字
     * @return  查询结果
     */
    fun getItemByFuzzyMatching(key: String): List<WfMarketItemEntity>?

    /**
     * 超模糊查询
     *
     * @param key 关键字
     * @return 查询结果
     */
    fun fuzzyQuery(key: String): List<WfMarketItemEntity?>
}