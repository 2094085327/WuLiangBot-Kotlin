package bot.demo.txbot.warframe.vo


/**
 * @description: Warframe Market Vo层
 * @author Nature Zero
 * @date 2024/8/21 上午9:34
 */
class WfMarketVo {
    /**
     * Warframe 市场物品
     *
     *
     * @property platinum 价格
     * @property quantity 数量
     * @property inGameName 游戏内名称
     */
    data class OrderInfo(
        val platinum: Int,
        val quantity: Int,
        val inGameName: String,
    )

    /**
     * Warframe 紫卡信息
     *
     * @property value 属性值
     * @property positive 是否为正属性
     * @property urlName 属性URL名
     */
    data class Attributes(
        val value: Double,
        val positive: Boolean,
        val urlName: String
    )

    /**
     * Warframe 紫卡订单信息
     *
     * @property modRank mod等级
     * @property reRolls 循环次数
     * @property startPlatinum 起拍价格
     * @property buyOutPlatinum 一口价
     * @property polarity 极性
     * @property positive 属性
     */
    data class RivenOrderInfo(
        val user: String,
        val userStatus: String,
        val modName: String,
        val modRank: Int,
        val reRolls: Int,
        val masteryLevel: Int,
        val startPlatinum: Int,
        val buyOutPlatinum: Int,
        val polarity: String,
        val positive: MutableList<Attributes>,
        val negative: MutableList<Attributes>,
        val updateTime: String,
    )

    /**
     * 玄骸武器订单
     *
     * @property element 元素
     * @property havingEphemera 是否有幻纹
     * @property damage 伤害
     * @property startPlatinum 起拍价
     * @property buyOutPlatinum 一口价
     */
    data class LichOrderInfo(
        val element: String,
        val havingEphemera: Boolean,
        val damage: Int,
        val startPlatinum: Int,
        val buyOutPlatinum: Int,
    )

    /**
     * 玄骸武器Entity
     *
     * @property lichName 玄骸武器名称
     * @property lichOrderInfoList 玄骸武器订单列表
     */
    data class LichEntity(
        val lichName: String,
        val lichOrderInfoList: List<LichOrderInfo>
    )

    /**
     * 紫卡订单列表
     *
     * @property itemName  物品名称
     * @property orderList 订单列表
     */
    data class RivenOrderList(
        val itemName: String,
        val orderList: List<RivenOrderInfo>
    )
}