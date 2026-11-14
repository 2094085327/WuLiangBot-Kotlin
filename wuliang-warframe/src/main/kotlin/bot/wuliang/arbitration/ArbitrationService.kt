package bot.wuliang.arbitration

import bot.wuliang.translation.PublicExportService
import bot.wuliang.utils.TimeUtils.formatDuration
import org.springframework.stereotype.Service
import java.time.Duration

/** 从本地生息效率基准计算评级，未收录或低于C档的节点显示“未分级”。 */
@Service
class ArbitrationService(
    private val source: ArbitrationScheduleSource,
    private val translations: PublicExportService,
    baselines: ArbitrationBaselines,
) {
    private data class NodeBaseline(val vitusPerHour: Double, val tier: ArbitrationTier)

    private val nodeBaselines = baselines.vitusPerHour.mapValues { (_, value) ->
        NodeBaseline(value, ArbitrationTier.fromVitusPerHour(value))
    }

    private fun tierFor(nodeId: String): ArbitrationTier = nodeBaselines[nodeId]?.tier ?: ArbitrationTier.UNRATED

    fun query(
        table: Boolean = false,
        filter: String = "所有",
        page: Int = 1,
        now: Long = System.currentTimeMillis()
    ): ArbitrationReport {
        require(filter in FILTERS) { "评级筛选无效，可使用：${FILTERS.joinToString("、")}" }
        require(page in 1..100000) { "页码需为 1–100000 的整数。" }
        val snapshot = source.snapshot(now)
        val schedule = snapshot.schedule
        val current = schedule.currentIndex(now)
        fun mission(index: Int): ArbitrationMission? {
            if (index !in 0 until schedule.size) return null
            val nodeId = schedule.nodeIdAt(index)
            val node = translations.node(nodeId)
            val baseline = nodeBaselines[nodeId]
            val activation = schedule.activationAt(index)
            val expiry = schedule.expiryAt(index)
            return ArbitrationMission(
                nodeId, node.name ?: nodeId, node.type ?: "未知任务", node.faction ?: "未知派系",
                (baseline?.tier ?: ArbitrationTier.UNRATED).label, baseline?.vitusPerHour, activation, expiry,
                formatDuration(Duration.ofMillis((if (activation > now) activation else expiry) - now))
            )
        }

        var nextHighValue = current + 1
        while (nextHighValue < schedule.size && !tierFor(schedule.nodeIdAt(nextHighValue)).highValue) nextHighValue++

        val items = ArrayList<ArbitrationMission>(if (table) PAGE_SIZE else 0)
        var total = 0
        if (table) {
            val offset = (page - 1) * PAGE_SIZE
            if (filter == "所有") {
                total = schedule.size - current
                val start = current + offset
                for (index in start until minOf(start + PAGE_SIZE, schedule.size)) items.add(requireNotNull(mission(index)))
            } else {
                // 仅计数并保留当前页，不生成全量筛选列表。
                for (index in current until schedule.size) {
                    val tier = tierFor(schedule.nodeIdAt(index))
                    if (filter != tier.label && !(filter == "高价值" && tier.highValue)) continue
                    if (total >= offset && total < offset + PAGE_SIZE) items.add(requireNotNull(mission(index)))
                    total++
                }
            }
        }
        val totalPages = (total + PAGE_SIZE - 1) / PAGE_SIZE
        require(!table || page == 1 || page <= totalPages) { "当前筛选共${totalPages}页。" }
        return ArbitrationReport(
            mode = if (table) "table" else "current", generatedAt = now,
            current = mission(current), next = mission(current + 1), nextHighValue = mission(nextHighValue),
            items = items,
            filter = filter, page = page, total = total, totalPages = totalPages, hasNext = page < totalPages,
            scheduleThrough = schedule.expiry, stale = false,
        )
    }

    companion object {
        const val PAGE_SIZE = 15
        val FILTERS = setOf("所有", "高价值") + ArbitrationTier.entries.map { it.label }
    }
}