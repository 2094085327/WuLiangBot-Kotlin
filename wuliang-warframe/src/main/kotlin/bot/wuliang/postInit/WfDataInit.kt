package bot.wuliang.postInit

import bot.wuliang.config.WARFRAME_DATA
import bot.wuliang.config.WfMarketConfig.WF_MARKET_CACHE_KEY
import bot.wuliang.moudles.Boss
import bot.wuliang.moudles.Info
import bot.wuliang.moudles.Modifiers
import bot.wuliang.moudles.Nodes
import bot.wuliang.redis.RedisService
import com.fasterxml.jackson.databind.ObjectMapper
import kotlinx.coroutines.*
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.io.File
import javax.annotation.PostConstruct

@Component
class WfDataInit {
    private val objectMapper = ObjectMapper()

    @Autowired
    private lateinit var redisService: RedisService

    @PostConstruct
    fun initData() = runBlocking {
        coroutineScope {
            launch { initSortie() }
            launch { initMissionType() }
            launch { initNodes() }
            launch { initSteelPath() }
            launch { initLanguage() }
            launch { initFissureModifiers() }
        }
    }

    /**
     * 初始化突击数据
     */
    private suspend fun initSortie() {
        if (redisService.hasKeyWithPrefix("${WF_MARKET_CACHE_KEY}Boss:*")) return
        val sortieJson = withContext(Dispatchers.IO) {
            objectMapper.readTree(File("$WARFRAME_DATA/sortieData.json"))
        }
        val bosses = sortieJson["bosses"]
        bosses.fields().forEach { (key, value) ->
            val boss = Boss(
                name = value["name"].asText(),
                faction = value["faction"].asText()
            )
            redisService.setValue("${WF_MARKET_CACHE_KEY}Boss:${key}", boss)
        }
        val modifierTypes = sortieJson["modifierTypes"]
        modifierTypes.fields().forEach { (key, value) ->
            redisService.setValue("${WF_MARKET_CACHE_KEY}ModifierType:${key}", value.asText())
        }
    }

    /**
     * 初始化任务类型
     */
    private suspend fun initMissionType() {
        if (redisService.hasKeyWithPrefix("${WF_MARKET_CACHE_KEY}MissionType:*")) return
        val missionTypeJson = withContext(Dispatchers.IO) {
            objectMapper.readTree(File("$WARFRAME_DATA/missionTypes.json"))
        }
        missionTypeJson.fields().forEach { (key, value) ->
            redisService.setValue("${WF_MARKET_CACHE_KEY}MissionType:${key}", value["value"].asText())
        }
    }

    /**
     * 初始化任务节点
     */
    private suspend fun initNodes() {
        if (redisService.hasKeyWithPrefix("${WF_MARKET_CACHE_KEY}Node:*")) return
        val nodesJson = withContext(Dispatchers.IO) {
            objectMapper.readTree(File("$WARFRAME_DATA/solNodes.json"))
        }
        nodesJson.fields().forEach { (key, value) ->
            val node = Nodes(
                name = value["value"]?.asText(),
                faction = value["enemy"]?.asText(),
                type = value["type"]?.asText()
            )
            redisService.setValue("${WF_MARKET_CACHE_KEY}Node:${key}", node)
        }
    }

    /**
     * 初始化钢铁之路奖励池
     */
    private suspend fun initSteelPath() {
        if (redisService.hasKey("${WF_MARKET_CACHE_KEY}SteelPath:Rotation")) return
        val steelPathJson = withContext(Dispatchers.IO) {
            objectMapper.readTree(File("$WARFRAME_DATA/steelPath.json"))
        }
        redisService.setValue("${WF_MARKET_CACHE_KEY}SteelPath:Rotation", steelPathJson["rotation"])
    }

    /**
     * 初始化内部翻译
     */
    private suspend fun initLanguage() {
        if (redisService.hasKeyWithPrefix("${WF_MARKET_CACHE_KEY}Languages:*")) return
        val languageJson = withContext(Dispatchers.IO) {
            objectMapper.readTree(File("$WARFRAME_DATA/languages.json"))
        }
        languageJson.fields().forEach { (key, value) ->
            redisService.setValue(
                "${WF_MARKET_CACHE_KEY}Languages:${key}",
                Info(value = value["value"]?.textValue(), desc = value["desc"]?.textValue())
            )
        }
    }

    /**
     * 初始化裂缝类型
     */
    private suspend fun initFissureModifiers() {
        if (redisService.hasKeyWithPrefix("${WF_MARKET_CACHE_KEY}FissureModifier:*")) return
        val fissureModifiersJson = withContext(Dispatchers.IO) {
            objectMapper.readTree(File("$WARFRAME_DATA/fissureModifiers.json"))
        }
        fissureModifiersJson.fields().forEach { (key, value) ->
            redisService.setValue(
                "${WF_MARKET_CACHE_KEY}FissureModifier:${key}",
                Modifiers(value = value["value"]?.textValue(), num = value["num"]?.asInt())
            )
        }
    }
}