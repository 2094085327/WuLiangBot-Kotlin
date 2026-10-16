package bot.wuliang.market

import bot.wuliang.botLog.logUtil.LoggerUtils.logError
import bot.wuliang.config.WARFRAME_MARKET_LICH_AUCTIONS
import bot.wuliang.config.WARFRAME_MARKET_RIVEN_AUCTIONS
import bot.wuliang.config.WARFRAME_MARKET_SISTER_AUCTIONS
import bot.wuliang.httpUtil.HttpUtil
import bot.wuliang.riven.RivenGroups
import bot.wuliang.riven.RivenQueryCriteria
import com.fasterxml.jackson.databind.JsonNode
import org.springframework.stereotype.Service

/** 访问紫卡、玄骸和姐妹拍卖接口，统一处理查询参数与请求失败。 */
@Service
class MarketAuctionSource {

    /** 构造按价格升序的玄骸拍卖参数；元素或幻纹条件为空时不加入该条件。 */
    private fun createLichQueryParams(
        weaponUrlName: String,
        element: String? = null,
        ephemera: Boolean? = false
    ): MutableMap<String, String> {
        return mutableMapOf(
            "weapon_url_name" to weaponUrlName,
            "sort_by" to "price_asc"
        ).apply {
            if (ephemera != null) put("having_ephemera", ephemera.toString())
            if (element != null) put("element", element)
        }
    }

    /** 按结构化紫卡条件请求 Market 拍卖接口，失败时记录日志并返回 null。 */
    fun getRivenAuctionsJson(criteria: RivenQueryCriteria): JsonNode? = try {
        HttpUtil.doGetJson(WARFRAME_MARKET_RIVEN_AUCTIONS, params = criteria.toMarketParams())
    } catch (e: Exception) {
        logError("WM紫卡查询错误:$e")
        null
    }

    /** 请求玄骸或姐妹拍卖，按元素及幻纹条件筛选；请求失败时返回 null。 */
    fun getLichAuctionsJson(
        element: String? = null,
        ephemera: String? = null,
        itemEntityUrlName: String,
        lichType: String? = null
    ): JsonNode? {
        val ephemeraBoolean = ephemera?.contains("有")
        val queryParams = createLichQueryParams(itemEntityUrlName, element = element, ephemera = ephemeraBoolean)

        return try {
            when (lichType) {
                RivenGroups.LICH -> HttpUtil.doGetJson(WARFRAME_MARKET_LICH_AUCTIONS, params = queryParams)
                else -> HttpUtil.doGetJson(WARFRAME_MARKET_SISTER_AUCTIONS, params = queryParams)
            }
        } catch (e: Exception) {
            logError("WM玄骸查询错误:$e")
            null
        }
    }
}