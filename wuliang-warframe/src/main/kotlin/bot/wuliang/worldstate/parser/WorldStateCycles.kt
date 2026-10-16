package bot.wuliang.worldstate.parser

import bot.wuliang.worldstate.model.WorldEntry
import com.fasterxml.jackson.databind.JsonNode
import java.time.Instant

/** 世界状态面板与机器人共用周期算法；每次按查询时间计算，避免缓存冻结倒计时。 */
object WorldStateCycles {
    private const val EARTH_PHASE = 14_400_000L
    private const val CETUS_DAY = 6_000_000L
    private const val CETUS_NIGHT = 3_000_000L
    private const val VALLIS_WARM = 400_000L
    private const val VALLIS_CYCLE = 1_600_000L
    private val vallisAnchor = Instant.parse("2026-02-04T19:46:48Z").toEpochMilli()

    /** 按集团标识 [tag] 选择 [now] 时刻有效且开始时间最新的集团记录。 */
    private fun syndicate(root: JsonNode, tag: String, now: Long): JsonNode? =
        root.path("SyndicateMissions").activeRecords(now).filter { it.text("Tag") == tag }
            .maxByOrNull { it.timestamp("Activation")!! }

    /**
     * 根据官方集团记录和固定周期锚点计算地球、平原、双衍及扎里曼的当前状态。
     *
     * @param root 完整官方世界状态。
     * @param now 本次查询的 Unix 毫秒时间，所有周期共用此时刻。
     * @return 可计算的周期条目；缺少有效集团记录的条目不加入结果。
     */
    fun parse(root: JsonNode, now: Long = System.currentTimeMillis()): List<WorldEntry> = buildList {
        val earthPhase = Math.floorMod(now, EARTH_PHASE * 2)
        val earthStart = now - earthPhase % EARTH_PHASE
        add(
            WorldEntry(
                "earth", "地球", subtitle = if (earthPhase < EARTH_PHASE) "白天" else "夜晚",
                activation = earthStart, expiry = earthStart + EARTH_PHASE
            )
        )
        syndicate(root, "CetusSyndicate", now)?.let { WorldStateTime.millis(it["Expiry"]) }?.let { end ->
            val day = end - now > CETUS_NIGHT
            val expiry = if (day) end - CETUS_NIGHT else end
            val activation = expiry - (if (day) CETUS_DAY else CETUS_NIGHT)
            add(
                WorldEntry(
                    "cetus", "希图斯", subtitle = if (day) "白天" else "夜晚",
                    activation = activation, expiry = expiry
                )
            )
            add(
                WorldEntry(
                    "cambion", "魔胎之境", subtitle = if (day) "Fass" else "Vome",
                    activation = activation, expiry = expiry
                )
            )
        }
        // 奥布山谷和双衍使用固定锚点推算，与世界状态面板保持一致。
        val vallisPhase = Math.floorMod(now - vallisAnchor, VALLIS_CYCLE)
        val warm = vallisPhase < VALLIS_WARM
        add(
            WorldEntry(
                "vallis", "奥布山谷", subtitle = if (warm) "温暖" else "寒冷",
                activation = now - vallisPhase + (if (warm) 0 else VALLIS_WARM),
                expiry = now + (if (warm) VALLIS_WARM else VALLIS_CYCLE) - vallisPhase,
                details = listOf("周期推算")
            )
        )
        val duviriPhase = Math.floorMod(now / 1000 - 52, 36_000)
        add(
            WorldEntry(
                "duviri", "双衍王境",
                subtitle = listOf("悲伤", "恐惧", "喜悦", "愤怒", "嫉妒")[(duviriPhase / 7200)],
                expiry = now + (7200 - duviriPhase % 7200) * 1000,
                details = listOf("周期推算")
            )
        )
        syndicate(root, "ZarimanSyndicate", now)?.takeIf { it.path("Seed").isIntegralNumber }?.let {
            add(
                WorldEntry(
                    "zariman", "扎里曼",
                    subtitle = if (it.path("Seed").asLong() and 1L == 0L) "Grineer" else "Corpus",
                    expiry = WorldStateTime.millis(it["Expiry"])
                )
            )
        }
    }
}