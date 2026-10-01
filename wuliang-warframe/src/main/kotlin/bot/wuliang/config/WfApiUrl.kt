package bot.wuliang.config

import bot.wuliang.config.CommonConfig.RESOURCES_PATH

val LANGUAGE_EN: MutableMap<String, Any> = mutableMapOf("language" to "en")
// 紫卡属性短名称仍使用 Market 中文目录。
val LANGUAGE_ZH_HANS: MutableMap<String, Any> = mutableMapOf("language" to "zh-hans")

const val WARFRAME_MARKET_BASE_URL = "https://api.warframe.market/v1"

const val WARFRAME_MARKET_BASE_URL_V2 = "https://api.warframe.market/v2"

const val WARFRAME_MARKET_VERSIONS_V2 = "$WARFRAME_MARKET_BASE_URL_V2/versions"

const val WARFRAME_STATUS_BASE_URL = "https://api.warframestat.us"

const val WARFRAME_STATUS_URL = "https://api.warframe.com/cdn/worldState.php"
const val WARFRAME_WEEKLY_RIVEN_PC = "https://www-static.warframe.com/repos/weeklyRivensPC.json"

const val WARFRAME_RESOURCES = "$RESOURCES_PATH/warframe"

const val WARFRAME_SPIRAL_SCHEDULE = "$WARFRAME_RESOURCES/data/spiral-schedule.json"
const val WARFRAME_SYNTHESIS_LOCATIONS = "$WARFRAME_RESOURCES/data/synthesis-locations.json"
const val WARFRAME_INCARNON = "$WARFRAME_RESOURCES/data/incarnon.json"
const val WARFRAME_AMP_PNG = "$WARFRAME_RESOURCES/img/amp.png"
const val WARFRAME_CETUS_WISP_PNG = "$WARFRAME_RESOURCES/img/cetusWisp.png"


/** 保留的 WarframeStat 掉率数据源，供后续奖励查询使用。 */
const val WARFRAME_DROP_DATA_URL = "https://drops.warframestat.us/data/all.slim.json"


/**
 * 仲裁节点数据
 *
 * @see <a href="https://github.com/calamity-inc/browse.wf">calamity-inc/browse.wf</a>
 */
const val WARFRAME_ARBYS_DATA = "https://browse.wf/arbys.txt"

/**
 * Market 物品
 */
const val WARFRAME_MARKET_ITEMS_V2 = "$WARFRAME_MARKET_BASE_URL_V2/items"
const val WARFRAME_MARKET_ITEMS_ORDERS_V2 = "$WARFRAME_MARKET_BASE_URL_V2/orders/item"

/**
 * Market 紫卡武器
 */
const val WARFRAME_MARKET_RIVEN_ITEMS_V2 = "$WARFRAME_MARKET_BASE_URL_V2/riven/weapons"

/**
 * Market 紫卡属性
 */
const val WARFRAME_MARKET_RIVEN_ATTRIBUTES_V2 = "$WARFRAME_MARKET_BASE_URL_V2/riven/attributes"

/**
 * Market 赤毒武器
 */
const val WARFRAME_MARKET_LICH_WEAPONS_V2 = "$WARFRAME_MARKET_BASE_URL_V2/lich/weapons"

/**
 * Market 信条武器
 */
const val WARFRAME_MARKET_SISTER_WEAPONS_V2 = "$WARFRAME_MARKET_BASE_URL_V2/sister/weapons"

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
