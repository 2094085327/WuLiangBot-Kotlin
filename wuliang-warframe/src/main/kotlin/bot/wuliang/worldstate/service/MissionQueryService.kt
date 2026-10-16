package bot.wuliang.worldstate.service

import bot.wuliang.config.WfMarketConfig.WF_ARCHONHUNT_KEY
import bot.wuliang.config.WfMarketConfig.WF_FISSURE_KEY
import bot.wuliang.config.WfMarketConfig.WF_INVASIONS_KEY
import bot.wuliang.config.WfMarketConfig.WF_NIGHTWAVE_KEY
import bot.wuliang.config.WfMarketConfig.WF_SORTIE_KEY
import bot.wuliang.moudles.Fissure
import bot.wuliang.moudles.Invasions
import bot.wuliang.moudles.NightWave
import bot.wuliang.moudles.Sortie
import bot.wuliang.redis.RedisService
import bot.wuliang.translation.PublicExportService
import bot.wuliang.utils.TimeUtils.parseDuration
import bot.wuliang.worldstate.parser.*
import bot.wuliang.worldstate.presentation.MissionResponseMapper
import bot.wuliang.worldstate.source.WorldStateSource
import com.fasterxml.jackson.databind.JsonNode
import org.springframework.stereotype.Service
import java.util.concurrent.TimeUnit

/** 负责玩法响应缓存；协议解析由共用解析器完成，页面字段由响应映射器生成。 */
@Service
class MissionQueryService(
    private val source: WorldStateSource,
    private val redis: RedisService,
    private val translations: PublicExportService,
    private val mapper: MissionResponseMapper,
) {
    /** 显式刷新当前突击；没有有效任务时移除上一轮缓存。 */
    fun refreshSortie(records: JsonNode): Sortie? = cacheSortie(records, "Variants", WF_SORTIE_KEY)

    /** 显式刷新执刑官猎杀，缺少有效任务时删除上一轮缓存。 */
    fun refreshArchonHunt(records: JsonNode): Sortie? = cacheSortie(records, "Missions", WF_ARCHONHUNT_KEY)

    /** 选取当前有效任务并映射为页面响应，使用带翻译版本的缓存键保存至结束边界。 */
    private fun cacheSortie(records: JsonNode, missionField: String, key: String): Sortie? {
        translations.index()
        val now = System.currentTimeMillis()
        val cacheKey = translations.cacheKey(key)
        val value = SortieParser.parse(records, missionField, now)
        if (value == null) {
            redis.deleteKey(cacheKey)
            return null
        }
        return mapper.sortie(value, now).also {
            redis.setValueWithExpiry(cacheKey, it, ((value.expiry - now) / 1000).coerceAtLeast(1), TimeUnit.SECONDS)
        }
    }

    /** 供图片指令预加载；缓存未命中才获取快照，与页面查询共用同一个缓存入口。 */
    fun getFissures(): List<Fissure> {
        translations.index()
        redis.getValueTyped<List<Fissure>>(translations.cacheKey(WF_FISSURE_KEY))?.let { return it }
        val data = source.get()
        return getFissures(data.path("ActiveMissions"), data.path("VoidStorms"))
    }

    /** 裂缝响应缓存不跨越最近的任务结束时间，最长保留五分钟。 */
    fun getFissures(records: JsonNode, storms: JsonNode): List<Fissure> {
        translations.index()
        val cacheKey = translations.cacheKey(WF_FISSURE_KEY)
        redis.getValueTyped<List<Fissure>>(cacheKey)?.let { return it }
        val now = System.currentTimeMillis()
        val states = FissureParser.parse(records, false, now) + FissureParser.parse(storms, true, now)
        val values = states.map { mapper.fissure(it, now) }.sortedBy { it.modifierValue }
        val ttl = states.minOfOrNull { ((it.expiry - now) / 1000).coerceAtLeast(1) }?.coerceAtMost(300) ?: 300
        redis.setValueWithExpiry(cacheKey, values, ttl, TimeUnit.SECONDS)
        return values
    }

    /** 重建电波赛季与挑战响应，按周挑战轮换边界维护缓存。 */
    fun refreshNightwave(season: JsonNode): NightWave {
        translations.index()
        val now = System.currentTimeMillis()
        val value = mapper.nightwave(NightwaveParser.parse(season, now), now)
        // 周常图片使用周挑战边界，不能因每日挑战提前失效。
        val records = season.path("ActiveChallenges").arrayItems().filter { !it.path("Daily").asBoolean() }
        val ttl = rotationCacheSeconds(records, now)
        redis.setValueWithExpiry(translations.cacheKey(WF_NIGHTWAVE_KEY), value, ttl, TimeUnit.SECONDS)
        return value
    }

    /** 优先读取入侵响应缓存，未命中时解析官方记录并写入分钟级缓存。 */
    fun getInvasions(records: JsonNode): List<Invasions> {
        translations.index()
        val cacheKey = translations.cacheKey(WF_INVASIONS_KEY)
        redis.getValueTyped<List<Invasions>>(cacheKey)?.let { return it }
        val values = InvasionParser.parse(records, System.currentTimeMillis()).map(mapper::invasion)
        // 保留既有入侵接口的分钟级缓存策略，与快照缓存相互独立。
        val ttl = values.minOfOrNull { it.eta?.parseDuration() ?: Long.MAX_VALUE }?.coerceIn(5, 10) ?: 10
        redis.setValueWithExpiry(cacheKey, values, ttl, TimeUnit.MINUTES)
        return values
    }
}