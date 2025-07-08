package bot.wuliang.postInit

import bot.wuliang.config.WARFRAME_DATA
import bot.wuliang.config.WfMarketConfig.WF_MARKET_CACHE_KEY
import bot.wuliang.moudles.Boss
import bot.wuliang.moudles.Nodes
import bot.wuliang.redis.RedisService
import com.fasterxml.jackson.databind.ObjectMapper
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
    fun initData() {

        initSortie()
        initMissionType()
        initNodes()
    }

    /**
     * 初始化突击数据
     */
    private fun initSortie() {
        if (redisService.hasKeyWithPrefix("${WF_MARKET_CACHE_KEY}Boss:*")) return
        val sortieJson = objectMapper.readTree(File("$WARFRAME_DATA/sortieData.json"))
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
    private fun initMissionType() {
        if (redisService.hasKeyWithPrefix("${WF_MARKET_CACHE_KEY}MissionType:*")) return
        val missionTypeJson = objectMapper.readTree(File("$WARFRAME_DATA/missionTypes.json"))
        missionTypeJson.fields().forEach { (key, value) ->
            redisService.setValue("${WF_MARKET_CACHE_KEY}MissionType:${key}", value["value"].asText())
        }
    }

    /**
     * 初始化任务节点
     */
    private fun initNodes() {
        if (redisService.hasKeyWithPrefix("${WF_MARKET_CACHE_KEY}Node:*")) return
        val nodesJson = objectMapper.readTree(File("$WARFRAME_DATA/solNodes.json"))
        nodesJson.fields().forEach { (key, value) ->
            val node = Nodes(
                name = value["value"]?.asText(),
                faction = value["enemy"]?.asText(),
                type = value["type"]?.asText()
            )
            redisService.setValue("${WF_MARKET_CACHE_KEY}Node:${key}", node)
        }
    }
}