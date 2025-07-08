package bot.wuliang.utils

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
    fun parseSorties(sortiesJson: JsonNode): Sortie? {
        if (redisService.hasKey(WF_SORTIE_KEY)) return redisService.getValueTyped<Sortie>(WF_SORTIE_KEY)
        val sortie = sortiesJson.get(0)
        val boss =
            sortie["Boss"]?.asText()?.let { redisService.getValueTyped<Boss>("${WF_MARKET_CACHE_KEY}Boss:${it}") }
        val sortieEntity = Sortie(
            id = sortie["_id"]["\$oid"].asText(),
            activation = sortie["Activation"]["\$date"]["\$numberLong"].asText()
                ?.let { Instant.ofEpochMilli(it.toLong()) },

            expiry = sortie["Expiry"]["\$date"]["\$numberLong"].asText()?.let { Instant.ofEpochMilli(it.toLong()) },
            boss = boss?.name,
            faction = boss?.faction!!.replaceFaction(),
            // 剩余时间 当前时间-结束时间
            eta = wfUtil.formatDuration(
                Duration.between(
                    Instant.now(), sortie["Expiry"]["\$date"]["\$numberLong"].asText()
                        ?.let { Instant.ofEpochMilli(it.toLong()) })
            ),
            variants = JacksonUtil.parseArray(
                { variant ->
                    Variants(
                        missionType = redisService.getValueTyped<String>("${WF_MARKET_CACHE_KEY}MissionType:${variant["missionType"]?.asText()}"),
                        modifierType = redisService.getValueTyped<String>("${WF_MARKET_CACHE_KEY}ModifierType:${variant["modifierType"]?.asText()}"),
                        node = redisService.getValueTyped<Nodes>("${WF_MARKET_CACHE_KEY}Node:${variant["node"]?.asText()}")!!.name,
                    )
                },
                sortie["Variants"]
            )
        )
        println(sortieEntity.eta)
        println(sortieEntity.eta?.parseDuration())
        redisService.setValueWithExpiry(
            WF_SORTIE_KEY,
            sortieEntity,
            sortieEntity.eta?.parseDuration() ?: 0L,
            TimeUnit.SECONDS
        )
        return sortieEntity
    }

    @Scheduled(cron = "1 0 8 * * 1")
    fun parseSteelPath():Pair<Long?,SteelPath?>  {
        if (redisService.hasKey(WF_STEELPATH_KEY)) return redisService.getExpireAndValueTyped<SteelPath>(WF_STEELPATH_KEY)
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