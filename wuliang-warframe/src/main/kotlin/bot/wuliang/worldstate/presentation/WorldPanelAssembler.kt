package bot.wuliang.worldstate.presentation

import bot.wuliang.translation.PublicExportIndex
import bot.wuliang.worldstate.model.WorldPanel
import bot.wuliang.worldstate.model.WorldSnapshot
import bot.wuliang.worldstate.parser.WorldStateCycles
import bot.wuliang.worldstate.parser.activeRecords
import bot.wuliang.worldstate.parser.text
import bot.wuliang.worldstate.parser.timestamp

/**
 * 将一份官方快照组装为后台面板。
 *
 * 整个面板共用查询时刻和词库上下文，玩法解析与原有接口复用；此处不请求网络、不写入 Redis。
 */
class WorldPanelAssembler(
    index: PublicExportIndex,
    nameLookup: (String) -> String = index::name,
    descriptionLookup: (String) -> String? = index::description,
    conquestLookup: (String) -> String = index::name,
    calendarLookup: (String) -> String = index::name,
) {
    private val display = WorldStateDisplay(index, nameLookup, descriptionLookup, conquestLookup, calendarLookup)
    private val missions = MissionSections(display)
    private val bounties = BountySections(display)
    private val weekly = WeeklySections(display)
    private val events = EventSections(display)
    private val traders = TraderSections(display)

    /**
     * 按照固定板块顺序组装快照、周期和玩法数据，并计算面板过期标记。
     *
     * @param snapshot 一次成功获取或允许回退的官方快照。
     * @param now 本次查询的 Unix 毫秒时间，所有板块使用同一时刻。
     * @return 后台面板响应，不写入玩法缓存。
     */
    fun assemble(snapshot: WorldSnapshot, now: Long = System.currentTimeMillis()): WorldPanel {
        val root = snapshot.data
        val syndicates = root.path("SyndicateMissions").activeRecords(now).groupBy { it.text("Tag") }
            .mapValues { (_, records) -> records.maxBy { it.timestamp("Activation")!! } }
        return WorldPanel(
            fetchedAt = snapshot.fetchedAt, worldTime = root.path("Time").asLong() * 1000, serverTime = now,
            stale = snapshot.stale || now - root.path("Time").asLong() * 1000 > 600_000,
            cycles = WorldStateCycles.parse(root, now),
            sections = listOf(
                missions.fissures(root, now),
                bounties.classicBounties(syndicates),
                bounties.challengeBounties(syndicates),
                missions.nightwave(root, now),
                missions.sorties(root, now),
                weekly.weekly(root, now),
                events.events(root, now),
                traders.traders(root, now, false),
                traders.traders(root, now, true),
                traders.traders(root, now, true, "EvergreenManifest"),
                missions.invasions(root, now),
                events.deals(root, now),
            ),
        )
    }
}