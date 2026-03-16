package bot.wuliang.utils

import bot.wuliang.botLog.logUtil.LoggerUtils.logInfo
import bot.wuliang.config.WARFRAME_BASE_PUBLIC_EXPORT_MANIFEST
import bot.wuliang.httpUtil.HttpUtil
import bot.wuliang.httpUtil.ProxyUtil
import bot.wuliang.jacksonUtil.JacksonUtil
import bot.wuliang.parser.model.RawItemData
import bot.wuliang.parser.model.wikia.WikiaData
import bot.wuliang.parser.model.wikia.WikiaDucats
import bot.wuliang.parser.scraper.WeaponScraper
import bot.wuliang.parser.scraper.WikiaDataScraper
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

@Component
class WfLexiconUtil {

    @Autowired
    private lateinit var wfScraper: WfScraper

    @Autowired
    private lateinit var wfHashManager: WfHashManager

    @Autowired
    private lateinit var proxyUtil: ProxyUtil


    sealed class EndpointResult {
        data class Manifest(val value: String) : EndpointResult()
        data class Endpoints(val list: List<String>) : EndpointResult()
    }

    data class CategoryData(
        val category: String,
        val data: MutableList<JsonNode>
    )

    fun init() {
        wfScraper.checkOriginServerAvailability()
        val currentHashes: Map<String, String> = wfHashManager.updateExportCache()
        val isUpdated = wfHashManager.isHashUpdated(currentHashes)
        if (isUpdated) {
            logInfo("Warframe 数据已更新")
            return
        }
        val rawItemData = RawItemData(api = fetchResources(), wikia = fetchWikiaData())

        wfHashManager.saveToRedis(currentHashes)
    }


    fun fetchEndpoint(endpoint: String): CategoryData {
        // 获取分类名称
        val category = endpoint.replace("Export", "").replace(Regex("_[a-z]{2}\\.json.*"), "")
        val raw = HttpUtil.doGetJson("${WARFRAME_BASE_PUBLIC_EXPORT_MANIFEST}/$endpoint")
        //  获取到分类的JSON数组
        val data = (raw["Export${category}"] as ArrayNode).toMutableList()

        // 突击奖励
        if (category == "SortieRewards") {
            // 将电波的突击奖励合并到突击奖励中
            data.addAll(raw["ExportNightwave"]["challenges"])
        }

        if (category == "Weapons") {
            data.addAll(raw["ExportRailjackWeapons"])
        }

        if (category == "Warframes") {
            // 添加Helminth
            val helminth = mapOf(
                "uniqueName" to "/Lotus/Powersuits/PowersuitAbilities/Helminth",
                "name" to "Helminth",
                "health" to 0,
                "shield" to 0,
                "armor" to 0,
                "stamina" to 0,
                "power" to 0,
                "abilities" to raw["ExportAbilities"]
            )
            val helminthJson = JacksonUtil.readTree(helminth)
            data.add(helminthJson)
        }

        if (category == "Upgrades") {
            // 添加Mod Set
            val modSets = raw["ExportModSet"].map { modSet ->
                JacksonUtil.readTree(modSet)
            }
            data.addAll(modSets)
            // 添加Avionics
            data.addAll(raw["ExportAvionics"])
            // 添加Focus Upgrades
            data.addAll(raw["ExportFocusUpgrades"])
        }

        return CategoryData(category, data)
    }

    fun fetchResources(): MutableList<CategoryData> {
        val endpoints = wfScraper.fetchEndpoints()
        val zhEndpoints = wfScraper.fetchEndpoints(locale = "zh")
        val result = mutableListOf<CategoryData>()
        // 并发对endpoints进行fetchEndpoint
        logInfo("获取Warframe API 数据")
        if (endpoints is EndpointResult.Endpoints) {
            runBlocking {
                endpoints.list.map { endpoint ->
                    async(Dispatchers.IO) {
                        val fetchEndpoint = fetchEndpoint(endpoint)
                        result.addAll(listOf(fetchEndpoint))
                    }
                }.awaitAll()
            }
        }
        if (zhEndpoints is EndpointResult.Endpoints) {
            runBlocking {
                zhEndpoints.list.map { endpoint ->
                    async(Dispatchers.IO) {
                        val fetchEndpoint = fetchEndpoint(endpoint)
                        result.add(fetchEndpoint)
                    }
                }.awaitAll()
            }
        }
        return result
    }

    fun fetchWikiaData(): WikiaData {
        val ducats = WikiaDataScraper<MutableList<WikiaDucats>>().getDucats()

        val weaponScraper = WeaponScraper()
        val weapons = weaponScraper.scrape()
        return WikiaData(weapons = weapons, ducats = ducats)
    }
}