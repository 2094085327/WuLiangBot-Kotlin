package bot.wuliang.worldstate.service

import bot.wuliang.translation.PublicExportService
import bot.wuliang.worldstate.model.BountyBoard
import bot.wuliang.worldstate.model.BountyGroup
import bot.wuliang.worldstate.model.BountyReport
import bot.wuliang.worldstate.parser.BountyParser
import bot.wuliang.worldstate.parser.timestamp
import bot.wuliang.worldstate.presentation.BountySections
import bot.wuliang.worldstate.presentation.WorldStateDisplay
import bot.wuliang.worldstate.source.WorldStateSource
import com.fasterxml.jackson.databind.JsonNode
import org.springframework.stereotype.Service

/** 复用世界状态快照与面板赏金展示，合并并发转换并缓存六组结果。 */
@Service
class BountyQueryService(
    private val source: WorldStateSource,
    private val translations: PublicExportService,
) {
    private data class CachedBoards(
        val records: Map<String, JsonNode>,
        val translationTag: String,
        val boards: List<BountyBoard>,
        val refreshAt: Long,
    )

    private var cached: CachedBoards? = null

    /** 每次按当前时刻筛选有效轮换；快照内容、词库或轮换边界变化后立即重建。 */
    @Synchronized
    fun getBounties(): BountyReport {
        val translationTag = translations.cacheTag()
        val snapshot = source.snapshot()
        val now = System.currentTimeMillis()
        val records = BountyParser.currentSyndicates(snapshot.data, now)
            .filterKeys { tag -> BountyGroup.entries.any { it.tag == tag } }
        val previous = cached
        val boards = if (previous != null && previous.translationTag == translationTag &&
            previous.records == records && now < previous.refreshAt
        ) previous.boards else {
            val sections =
                BountySections(WorldStateDisplay(translations.index(), translations::name, translations::description))
            BountyGroup.entries.map { group ->
                val section = if (group.challenge) sections.challengeBounties(records, listOf(group.tag))
                else sections.classicBounties(records, listOf(group.tag))
                BountyBoard(
                    group.id, group.command, section,
                    records[group.tag]?.timestamp("Activation"), records[group.tag]?.timestamp("Expiry")
                )
            }.also { values ->
                // 缺数据或配置失败时短暂缓存，避免长时间冻结缺失状态。
                val ttl = if (values.any { it.section.entries.isEmpty() }) 30_000 else 300_000
                cached = CachedBoards(records, translationTag, values, now + ttl)
            }
        }
        return BountyReport(
            boards, snapshot.fetchedAt, now,
            snapshot.stale || now - snapshot.data.path("Time").asLong() * 1000 > 600_000
        )
    }
}