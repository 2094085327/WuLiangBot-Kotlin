package bot.demo.txbot.warframe

val LANGUAGE_ZH_HANS: MutableMap<String, Any> = mutableMapOf("language" to "zh-hans")
val LANGUAGE_EN_HANS: MutableMap<String, Any> = mutableMapOf("language" to "en")

const val WARFRAME_MARKET_BASE_URL = "https://api.warframe.market/v1"

/**
 * 赤毒幻纹
 */
const val WARFRAME_MARKET_LICH_EPHEMERAS = "$WARFRAME_MARKET_BASE_URL/lich/ephemeras"

/**
 * 信条幻纹
 */
const val WARFRAME_MARKET_SISTER_EPHEMERAS = "$WARFRAME_MARKET_BASE_URL/sister/ephemeras"

/**
 * Market 物品
 */
const val WARFRAME_MARKET_ITEMS = "$WARFRAME_MARKET_BASE_URL/items"

/**
 * Market 紫卡武器
 */
const val WARFRAME_MARKET_RIVEN_ITEMS = "$WARFRAME_MARKET_BASE_URL/riven/items"

/**
 * Market 紫卡属性
 */
const val WARFRAME_MARKET_RIVEN_ATTRIBUTES = "$WARFRAME_MARKET_BASE_URL/riven/attributes"

/**
 * Market 紫卡拍卖
 */
const val WARFRAME_MARKET_RIVEN_AUCTIONS = "$WARFRAME_MARKET_BASE_URL/auctions/search?type=riven"

/**
 * Market 玄骸武器拍卖
 */
const val WARFRAME_MARKET_LICH = "$WARFRAME_MARKET_BASE_URL/auctions/search?type=lich"

/**
 * 赤毒武器
 */
const val WARFRAME_MARKET_LICH_WEAPONS = "$WARFRAME_MARKET_BASE_URL/lich/weapons"

/**
 * 信条武器
 */
const val WARFRAME_MARKET_SISTER_WEAPONS = "$WARFRAME_MARKET_BASE_URL/sister/weapons"

/**
 * 地图地点
 */
const val WARFRAME_MARKET_LOCATION = "$WARFRAME_MARKET_BASE_URL/locations"
