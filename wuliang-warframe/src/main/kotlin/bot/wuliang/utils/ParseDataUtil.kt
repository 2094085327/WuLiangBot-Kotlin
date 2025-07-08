package bot.wuliang.utils

import bot.wuliang.config.WfMarketConfig.WF_MARKET_CACHE_KEY
import bot.wuliang.config.WfMarketConfig.WF_SORTIE_KEY
import bot.wuliang.jacksonUtil.JacksonUtil
import bot.wuliang.moudles.Boss
import bot.wuliang.moudles.Nodes
import bot.wuliang.moudles.Sortie
import bot.wuliang.moudles.Variants
import bot.wuliang.redis.RedisService
import bot.wuliang.utils.WfStatus.parseDuration
import bot.wuliang.utils.WfStatus.replaceFaction
import com.fasterxml.jackson.databind.JsonNode
import org.springframework.beans.factory.annotation.Autowired
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
}