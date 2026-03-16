package bot.wuliang.config

import bot.wuliang.config.CommonConfig.RESOURCES_PATH

val LANGUAGE_ZH_HANS: MutableMap<String, Any> = mutableMapOf("language" to "zh-hans")
val LANGUAGE_EN_HANS: MutableMap<String, Any> = mutableMapOf("language" to "en")

const val WARFRAME_MARKET_BASE_URL = "https://api.warframe.market/v1"

const val WARFRAME_MARKET_BASE_URL_V2 = "https://api.warframe.market/v2"

const val WARFRAME_STATUS_BASE_URL = "https://api.warframestat.us"

const val WARFRAME_STATUS_URL = "https://content.warframe.com/dynamic/worldState.php"
const val WARFRAME_WEEKLY_RIVEN_PC = "https://www-static.warframe.com/repos/weeklyRivensPC.json"

const val WARFRAME_RESOURCES = "$RESOURCES_PATH/warframe"

const val WARFRAME_INCARNON = "$WARFRAME_RESOURCES/incarnon.json"
const val WARFRAME_MOOD_SPIRALS = "$WARFRAME_RESOURCES/mood_spirals.json"
const val WARFRAME_AMP_PNG = "$WARFRAME_RESOURCES/img/amp.png"
const val WARFRAME_CETUS_WISP_PNG = "$WARFRAME_RESOURCES/img/cetusWisp.png"

const val WARFRAME_DATA = "$WARFRAME_RESOURCES/data"

/**
 * Warframe 官方API清单
 */
const val WARFRAME_BASE_PUBLIC_EXPORT = "https://origin.warframe.com/PublicExport/index_"
const val WARFRAME_BASE_PUBLIC_EXPORT_BACKUP = "https://content.warframe.com/PublicExport/index_"


/**
 * 仲裁节点数据
 *
 * @see <a href="https://github.com/calamity-inc/browse.wf">calamity-inc/browse.wf</a>
 */
const val WARFRAME_ARBYS_DATA = "https://browse.wf/arbys.txt"

/**
 * Warframe 英文Wiki链接
 */
const val WARFRAME_WIKIA_BASE_URL = "https://wiki.warframe.com/w"

/**
 * Warframe 英文Wiki API链接
 */
const val WARFRAME_WIKIA_API = "https://wiki.warframe.com/api.php"

/**
 * Wiki 部件杜卡德金币数据
 */
const val WARFRAME_WIKIA_DUCATS = "$WARFRAME_WIKIA_BASE_URL/Ducats/Prices/All"

/**
 * Wiki 武器数据
 */
const val WARFRAME_WIKIA_WEAPONS = "$WARFRAME_WIKIA_BASE_URL/Module:Weapons/data"

/**
 * Wiki 蓝图数据
 */
const val WARFRAME_WIKIA_BLUEPRINTS = "$WARFRAME_WIKIA_BASE_URL/Module:Blueprints/data"

/**
 * Market 物品
 */
const val WARFRAME_MARKET_ITEMS_V2 = "$WARFRAME_MARKET_BASE_URL_V2/items"
const val WARFRAME_MARKET_ITEMS_ORDERS_V2 = "$WARFRAME_MARKET_BASE_URL_V2/orders/item"

const val WARFRAME_STATUS_ITEM = "$WARFRAME_STATUS_BASE_URL/items"

/**
 * Market 紫卡武器
 */
const val WARFRAME_MARKET_RIVEN_ITEMS_V2 = "$WARFRAME_MARKET_BASE_URL_V2/riven/weapons"

/**
 * Market 紫卡属性
 */
const val WARFRAME_MARKET_RIVEN_ATTRIBUTES_V2 = "$WARFRAME_MARKET_BASE_URL_V2/riven/attributes"

/**
 * Market 最新紫卡拍卖
 */
const val WARFRAME_MARKET_RIVEN_AUCTIONS_BASE = "$WARFRAME_MARKET_BASE_URL/auctions"

/**
 * WM紫卡
 */
const val WARFRAME_MARKET_RIVEN_AUCTIONS = "$WARFRAME_MARKET_RIVEN_AUCTIONS_BASE/search?type=riven"

/**
 * Market 玄骸武器拍卖
 */
const val WARFRAME_MARKET_LICH_AUCTIONS = "$WARFRAME_MARKET_RIVEN_AUCTIONS_BASE/search?type=lich"
const val WARFRAME_MARKET_SISTER_AUCTIONS = "$WARFRAME_MARKET_RIVEN_AUCTIONS_BASE/search?type=sister"

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

/**
 * 火卫二世界状态
 */
const val WARFRAME_STATUS_PHOBOS_STATUS = "$WARFRAME_STATUS_BASE_URL/pc/cambionCycle"

/**
 * 金星平原状态
 */
const val WARFRAME_STATUS_VENUS_STATUS = "$WARFRAME_STATUS_BASE_URL/pc/vallisCycle"

/**
 * 希图斯状态
 */
const val WARFRAME_STATUS_CETUS_STATUS = "$WARFRAME_STATUS_BASE_URL/pc/cetusCycle"

/**
 * 地球状态
 */
const val WARFRAME_STATUS_EARTH_STATUS = "$WARFRAME_STATUS_BASE_URL/pc/earthCycle"