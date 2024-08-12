package bot.demo.txbot.warframe

import bot.demo.txbot.other.RESOURCES_PATH

val LANGUAGE_ZH_HANS: MutableMap<String, Any> = mutableMapOf("language" to "zh-hans")
val LANGUAGE_EN_HANS: MutableMap<String, Any> = mutableMapOf("language" to "en")

const val WARFRAME_MARKET_BASE_URL = "https://api.warframe.market/v1"

const val WARFRAME_STATUS_BASE_URL = "https://api.warframestat.us"

const val WARFRAME_RESOURCES = "$RESOURCES_PATH/warframe"

const val WARFRAME_INCARNON = "$WARFRAME_RESOURCES/incarnon.json"
const val WARFRAME_NEW_INCARNON = "$WARFRAME_RESOURCES/new_incarnon.json"

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
 * Market 紫卡拍卖
 */
const val WARFRAME_MARKET_RIVEN_AUCTIONS = "$WARFRAME_MARKET_BASE_URL/auctions/search?type=riven"

/**
 * Market 玄骸武器拍卖
 */
const val WARFRAME_MARKET_LICH_AUCTIONS = "$WARFRAME_MARKET_BASE_URL/auctions/search?type=lich"
const val WARFRAME_MARKET_SISTER_AUCTIONS = "$WARFRAME_MARKET_BASE_URL/auctions/search?type=sister"

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
 * 希图斯状态
 */
const val WARFRAME_STATUS_CETUS_STATUS = "$WARFRAME_STATUS_BASE_URL/pc/cetusCycle"

/**
 * 入侵信息
 */
const val WARFRAME_STATUS_INVASIONS = "$WARFRAME_STATUS_BASE_URL/pc/invasions"