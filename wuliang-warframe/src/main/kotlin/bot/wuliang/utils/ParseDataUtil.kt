package bot.wuliang.utils

import bot.wuliang.config.WfMarketConfig.WF_ARCHONHUNT_KEY
import bot.wuliang.config.WfMarketConfig.WF_MARKET_CACHE_KEY
import bot.wuliang.config.WfMarketConfig.WF_SORTIE_KEY
import bot.wuliang.config.WfMarketConfig.WF_STEELPATH_KEY
import bot.wuliang.jacksonUtil.JacksonUtil
import bot.wuliang.moudles.*
import bot.wuliang.redis.RedisService
import bot.wuliang.utils.WfStatus.parseDuration
import bot.wuliang.utils.WfStatus.replaceFaction
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit

@Component
class ParseDataUtil {
    private val wfUtil = WfUtil()

    @Autowired
    private lateinit var redisService: RedisService

    /**
     * 通用解析常规突击任务（每日突击与周突击）
     */
    private fun parseCommonSortie(
        sortiesJson: JsonNode,
        cacheKey: String,
        missionKey:String
    ): Sortie? {
        if (redisService.hasKey(cacheKey)) return redisService.getValueTyped<Sortie>(cacheKey)
        val keyPrefix = WF_MARKET_CACHE_KEY
        val sortie = sortiesJson.get(0)
        val boss = sortie["Boss"]?.asText()
            ?.let { redisService.getValueTyped<Boss>("${keyPrefix}Boss:${it}") }

        data class SortieReward(val boss: String, val reward: String)

        val sortieRewards = listOf(
            SortieReward("欺谋狼主", "深红源力石"),
            SortieReward("混沌蛇主", "琥珀源力石"),
            SortieReward("诡文枭主", "蔚蓝源力石")
        )

        val bossName = boss?.name
        val currentIndex = sortieRewards.indexOfFirst { it.boss == bossName }

        val (rewardItem, nextBoss, nextRewardItem) = when {
            currentIndex >= 0 -> {
                val nextIndex = (currentIndex + 1) % sortieRewards.size
                Triple(
                    sortieRewards[currentIndex].reward,
                    sortieRewards[nextIndex].boss,
                    sortieRewards[nextIndex].reward
                )
            }
            else -> Triple(null,null,null)
        }


        val sortieEntity = Sortie(
            id = sortie["_id"]["\$oid"].asText(),
            activation = sortie["Activation"]["\$date"]["\$numberLong"].asText()
                ?.let { Instant.ofEpochMilli(it.toLong()) },
            expiry = sortie["Expiry"]["\$date"]["\$numberLong"].asText()
                ?.let { Instant.ofEpochMilli(it.toLong()) },
            boss = boss?.name,
            rewardItem = rewardItem,
            nextBoss = nextBoss,
            nextRewardItem = nextRewardItem,
            faction = boss?.faction!!.replaceFaction(),
            eta = wfUtil.formatDuration(
                Duration.between(
                    Instant.now(),
                    sortie["Expiry"]["\$date"]["\$numberLong"].asText()
                        ?.let { Instant.ofEpochMilli(it.toLong()) }
                )
            ),
            variants = JacksonUtil.parseArray({ variant ->
                Variants(
                    missionType = redisService.getValueTyped<String>("${keyPrefix}MissionType:${variant["missionType"]?.asText()}"),
                    modifierType = redisService.getValueTyped<String>("${keyPrefix}ModifierType:${variant["modifierType"]?.asText()}"),
                    node = redisService.getValueTyped<Nodes>("${keyPrefix}Node:${variant["node"]?.asText()}")!!.name
                )
            }, sortie[missionKey])
        )

        redisService.setValueWithExpiry(
            cacheKey,
            sortieEntity,
            sortieEntity.eta?.parseDuration() ?: 0L,
            TimeUnit.SECONDS
        )

        return sortieEntity
    }

    /**
     * 解析每日突击任务
     */
    fun parseSorties(sortiesJson: JsonNode): Sortie? {
        return parseCommonSortie(sortiesJson, WF_SORTIE_KEY,"Variants")
    }

    /**
     * 解析周突击任务
     */
    fun parseArchonHunt(sortiesJson: JsonNode): Sortie? {
        return parseCommonSortie(sortiesJson, WF_ARCHONHUNT_KEY,"Missions")
    }

    @Scheduled(cron = "1 0 8 * * 1")
    fun parseSteelPath(): Pair<Long?, SteelPath?> {
        if (redisService.hasKey(WF_STEELPATH_KEY)) return redisService.getExpireAndValueTyped<SteelPath>(
            WF_STEELPATH_KEY
        )
        val rotationJson = redisService.getValueTyped<ArrayNode>("${WF_MARKET_CACHE_KEY}SteelPath:Rotation")
        val start = Instant.parse("2020-11-16T00:00:00.000Z")
        val sSinceStart = Duration.between(start, Instant.now()).seconds
        val eightWeeks = 4838400
        val sevenDays = 604800

        val ind = ((sSinceStart % eightWeeks) / sevenDays).toInt()

        // 计算下周的索引
        val nextInd = (ind + 1) % 8 // 共有8个周期

        val activation = wfUtil.getFirstDayOfWeek()
        val expiry = wfUtil.getLastDayOfWeek()

        val steelPathEntity = SteelPath(
            id = "spi:${wfUtil.getStartOfDay().toEpochMilli()}",
            activation = activation,
            expiry = expiry,
            eta = wfUtil.formatDuration(Duration.between(Instant.now(), expiry)),
            currentItem = rotationJson!!.get(ind)["name"].asText(),
            currentCost = rotationJson.get(ind)["cost"].asInt(),
            nextItem = rotationJson.get(nextInd)["name"].asText(),
            nextCost = rotationJson.get(nextInd)["cost"].asInt()
        )
        redisService.setValueWithExpiry(
            WF_STEELPATH_KEY,
            steelPathEntity,
            steelPathEntity.eta?.parseDuration() ?: 0L,
            TimeUnit.SECONDS
        )
        return Pair(steelPathEntity.eta?.parseDuration(), steelPathEntity)
    }

}