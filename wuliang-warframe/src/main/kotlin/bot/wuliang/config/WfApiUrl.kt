package bot.wuliang.config

import bot.wuliang.config.CommonConfig.RESOURCES_PATH

val LANGUAGE_ZH_HANS: MutableMap<String, Any> = mutableMapOf("language" to "zh-hans")
val LANGUAGE_EN_HANS: MutableMap<String, Any> = mutableMapOf("language" to "en")

const val WARFRAME_MARKET_BASE_URL = "https://api.warframe.market/v1"

const val WARFRAME_STATUS_BASE_URL = "https://api.warframestat.us"

const val WARFRAME_STATUS_URL = "https://content.warframe.com/dynamic/worldState.php"

const val WARFRAME_RESOURCES = "$RESOURCES_PATH/warframe"

const val WARFRAME_INCARNON = "$WARFRAME_RESOURCES/incarnon.json"
const val WARFRAME_MOOD_SPIRALS = "$WARFRAME_RESOURCES/mood_spirals.json"
const val WARFRAME_AMP_PNG = "$WARFRAME_RESOURCES/img/amp.png"
const val WARFRAME_CETUS_WISP_PNG = "$WARFRAME_RESOURCES/img/cetusWisp.png"

const val WARFRAME_DATA = "$WARFRAME_RESOURCES/data"

/**
 * Market 物品
 */
const val WARFRAME_MARKET_ITEMS = "$WARFRAME_MARKET_BASE_URL/items"

const val WARFRAME_STATUS_ITEM = "$WARFRAME_STATUS_BASE_URL/items"

/**
 * Market 紫卡武器
 */
const val WARFRAME_MARKET_RIVEN_ITEMS = "$WARFRAME_MARKET_BASE_URL/riven/items"

/**
 * Market 紫卡属性
 */
const val WARFRAME_MARKET_RIVEN_ATTRIBUTES = "$WARFRAME_MARKET_BASE_URL/riven/attributes"

/**
 * Market 最新紫卡拍卖
 */
const val WARFRAME_MARKET_RIVEN_AUCTIONS_BASE = "$WARFRAME_MARKET_BASE_URL/auctions"

/**
 * 热门紫卡
 */
const val WARFRAME_MARKET_RIVEN_AUCTIONS_HOT = "$WARFRAME_MARKET_RIVEN_AUCTIONS_BASE/popular"
const val WARFRAME_MARKET_RIVEN_AUCTIONS = "$WARFRAME_MARKET_RIVEN_AUCTIONS_BASE/search?type=riven"
const val WARFRAME_WEEKLY_RIVEN = "https://www-static.warframe.com/repos/weeklyRivensPC.json"

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
 * 裂缝数据
 */
const val WARFRAME_STATUS_FISSURES = "$WARFRAME_STATUS_BASE_URL/pc/fissures/"

/**
 * 虚空商人
 */
const val WARFRAME_STATUS_VOID_TRADER = "$WARFRAME_STATUS_BASE_URL/pc/voidTrader"

/**
 * 钢铁之路
 */
const val WARFRAME_STATUS_STEEL_PATH = "$WARFRAME_STATUS_BASE_URL/pc/steelPath"

/**
 * 日常突击
 */
const val WARFRAME_STATUS_SORTIE = "$WARFRAME_STATUS_BASE_URL/pc/sortie"

/**
 * 执刑官突击
 */
const val WARFRAME_STATUS_ARCHON_HUNT = "$WARFRAME_STATUS_BASE_URL/pc/archonHunt"

/**
 * 午夜电波
 */
const val WARFRAME_STATUS_NIGHT_WAVE = "$WARFRAME_STATUS_BASE_URL/pc/nightwave"

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

/**
 * 入侵信息
 */
const val WARFRAME_STATUS_INVASIONS = "$WARFRAME_STATUS_BASE_URL/pc/invasions"