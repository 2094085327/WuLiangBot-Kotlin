package bot.wuliang.arbitration

import bot.wuliang.config.WarframeProperties
import bot.wuliang.translation.PublicExportService
import bot.wuliang.utils.TimeUtils.formatDuration
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration

/** 评级独立于排期，未收录节点保留“未评级”。 */
@Service
class ArbitrationService(
    private val source: ArbitrationScheduleSource,
    private val translations: PublicExportService,
    properties: WarframeProperties,
    mapper: ObjectMapper,
) {
    private val tiers: Map<String, String>
    private val highValue: Set<String>

    init {
        val ratings = Files.newInputStream(Path.of(properties.arbitration.tiersFile)).use { mapper.readTree(it) }
        tiers = ratings.path("tiers").fields().asSequence().associate { it.key to it.value.asText() }
        highValue = ratings.path("highValueTiers").map { it.asText() }.toSet()
        require(tiers.isNotEmpty() && tiers.values.all { it in FILTERS - "所有" - "高价值" - "未评级" }) { "仲裁评级表格式无效" }
        require(highValue.isNotEmpty() && highValue.all { it in tiers.values }) { "仲裁高价值评级配置无效" }
    }

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
            val activation = schedule.activationAt(index)
            val expiry = schedule.expiryAt(index)
            return ArbitrationMission(
                nodeId, node.name ?: nodeId, node.type ?: "未知任务", node.faction ?: "未知派系",
                tiers[nodeId] ?: "未评级", activation, expiry,
                formatDuration(Duration.ofMillis((if (activation > now) activation else expiry) - now))
            )
        }

        var nextHighValue = current + 1
        while (nextHighValue < schedule.size && tiers[schedule.nodeIdAt(nextHighValue)] !in highValue) nextHighValue++

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
                    val tier = tiers[schedule.nodeIdAt(index)] ?: "未评级"
                    if (filter != tier && !(filter == "高价值" && tier in highValue)) continue
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
        val FILTERS = setOf("所有", "高价值", "S", "A+", "A", "A-", "B", "C", "未评级")
    }
}