package bot.wuliang.entity.vo

import java.io.Serializable


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
     * @property userStatus 用户状态
     */
    data class OrderInfo(
        val platinum: Int,
        val quantity: Int,
        val inGameName: String,
        val userStatus: String,
    ) : Serializable

    /**
     * Warframe 紫卡信息
     *
     * @property value 属性值
     * @property positive 是否为正属性
     * @property slug v1 拍卖接口使用的属性标识
     * @property displayName 根据 v2 属性目录解析出的展示名
     * @property unit v2 属性目录提供的数值单位
     * @property formattedValue 已按单位格式化、可直接展示的属性值
     */
    data class Attributes(
        val value: Double,
        val positive: Boolean,
        val slug: String,
        val displayName: String,
        val unit: String?,
        val formattedValue: String,
    ) : Serializable

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
        val positive: List<Attributes>,
        val negative: List<Attributes>,
        val updateTime: String,
    ) : Serializable

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
        val element: String? = null,
        val havingEphemera: Boolean? = null,
        val damage: Int? = null,
        val startPlatinum: Int? = null,
        val buyOutPlatinum: Int? = null,
    ) : Serializable

    /**
     * 玄骸武器Entity
     *
     * @property lichName 玄骸武器名称
     * @property lichOrderInfoList 玄骸武器订单列表
     */
    data class LichEntity(
        val lichName: String? = null,
        val lichOrderInfoList: List<LichOrderInfo>? = listOf(),
        val currentPage: Int = 1,
        val totalPages: Int = 1,
    ) : Serializable

    /**
     * 紫卡订单列表
     *
     * @property itemName  物品名称
     * @property orderList 订单列表
     */
    data class RivenOrderList(
        val itemName: String,
        val orderList: List<RivenOrderInfo>,
        val currentPage: Int = 1,
        val totalPages: Int = 1,
    ) : Serializable
}