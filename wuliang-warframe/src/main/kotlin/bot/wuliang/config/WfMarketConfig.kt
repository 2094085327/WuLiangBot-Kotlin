package bot.wuliang.config


/**
 * @description: Warframe Market配置类
 * @author Nature Zero
 * @date 2025/1/5 22:51
 */
object WfMarketConfig {
    val SPECIAL_ITEMS_LIST = listOf("赋能·充沛")
    val REPLACE_LIST = listOf("蓝图", "总图")

    /** Warframe缓存key*/
    const val WF_MARKET_CACHE_KEY = "Wuliang:Warframe:"

    /**
     * 别名缓存
     */
    const val WF_ALL_OTHER_NAME_KEY = WF_MARKET_CACHE_KEY + "allOtherName"

    /**
     * 执刑官缓存
     */
    const val WF_ARCHONHUNT_KEY = WF_MARKET_CACHE_KEY + "archonHunt"

    /**
     * 每日突击缓存
     */
    const val WF_SORTIE_KEY = WF_MARKET_CACHE_KEY + "sortie"

    /**
     * 钢铁之路缓存
     */
    const val WF_STEELPATH_KEY = WF_MARKET_CACHE_KEY + "steelPath"

    /**
     * 虚空商人缓存
     */
    const val WF_VOIDTRADER_KEY = WF_MARKET_CACHE_KEY + "voidTrader"
    /**
     * 虚空商人到来缓存
     */
    const val WF_VOID_TRADER_COME_KEY = WF_MARKET_CACHE_KEY + "voidTraderCome"

    /**
     * 玄骸缓存
     */
    const val WF_LICHORDER_KEY = WF_MARKET_CACHE_KEY + "lichOrder:"

    /**
     * 电波缓存
     */
    const val WF_NIGHTWAVE_KEY = WF_MARKET_CACHE_KEY + "nightWave"

    /**
     * 电波缓存
     */
    const val WF_FISSURE_KEY = WF_MARKET_CACHE_KEY + "fissure"

    /**
     * 入侵缓存
     */
    const val WF_INVASIONS_KEY = WF_MARKET_CACHE_KEY + "invasions"

    /**
     * 本周灵化
     */
    const val WF_INCARNON_KEY = WF_MARKET_CACHE_KEY + "incarnon"

    /**
     * 本周灵化武器紫卡价格
     */
    const val WF_INCARNON_RIVEN_KEY = WF_MARKET_CACHE_KEY + "incarnonRiven"

    /**
     * 火卫二
     */
    const val WF_PHOBOS_STATUS_KEY = WF_MARKET_CACHE_KEY + "phobosStatus"

    /**
     * 夜灵平原
     */
    const val WF_CETUS_CYCLE_KEY = WF_MARKET_CACHE_KEY + "cetusCycle"

    /**
     * 地球
     */
    const val WF_EARTH_CYCLE_KEY = WF_MARKET_CACHE_KEY + "earthCycle"

    /**
     * 金星平原
     */
    const val WF_VENUS_STATUS_KEY = WF_MARKET_CACHE_KEY + "venusStatus"

    /**
     * 双衍平原
     */
    const val WF_MOODSPIRALS_KEY = WF_MARKET_CACHE_KEY + "moodSpirals"
}