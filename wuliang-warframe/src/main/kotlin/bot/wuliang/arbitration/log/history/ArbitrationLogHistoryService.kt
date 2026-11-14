package bot.wuliang.arbitration.log.history

import bot.wuliang.arbitration.ArbitrationBaselines
import bot.wuliang.arbitration.log.ArbitrationLogDetails
import bot.wuliang.arbitration.log.ArbitrationLogMath
import bot.wuliang.arbitration.log.archive.ArbitrationLogUploader
import bot.wuliang.mapper.ArbitrationLogArchiveMapper
import bot.wuliang.translation.PublicExportService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class ArbitrationLogHistoryService(
    private val mapper: ArbitrationLogArchiveMapper,
    private val translations: PublicExportService,
    private val baselines: ArbitrationBaselines,
) {
    @Transactional(readOnly = true, timeout = 5)
    fun report(uploader: ArbitrationLogUploader, page: Int): ArbitrationLogHistoryReport {
        require(page > 0) { "页码需为正整数。" }
        val senderKey = uploader.key()
        val total = mapper.historyCount(senderKey)
        require(total > 0) { "还没有保存的仲裁记录，请先发送主机的 EE.log。" }
        val totalPages = (total - 1) / ArbitrationLogHistoryQuery.PAGE_SIZE + 1
        require(page.toLong() <= totalPages) { "共${total}条仲裁记录，只有${totalPages}页。" }
        require(translations.ready) { PublicExportService.UNAVAILABLE_MESSAGE }
        val offset = (page.toLong() - 1) * ArbitrationLogHistoryQuery.PAGE_SIZE
        val items = mapper.historyPage(senderKey, offset, ArbitrationLogHistoryQuery.PAGE_SIZE).map { record ->
            val node = translations.node(record.nodeId)
            val valid = record.completed && !record.hasConflict
            val duration = record.durationSeconds?.takeIf { it.isFinite() && it > 0 }
            val proficiency = ArbitrationLogDetails.proficiency(
                record.drones, record.enemies, duration.takeIf { valid }, baselines.kpm[record.nodeId]
            ).percent
            val vitus = record.fullBonusVitusPerHour?.takeIf { valid && it.isFinite() && it >= 0 }
            ArbitrationLogHistoryItem(
                checkNotNull(record.recordId).also { check(it > 0) }.toString(),
                record.startedAt,
                node.name ?: record.nodeId,
                node.type ?: when (record.missionKind) {
                    "defense" -> "防御"
                    "mirrorDefense" -> "镜像防御"
                    "interception" -> "拦截"
                    "survival" -> "生存"
                    "disruption" -> "中断"
                    else -> "未知任务"
                },
                record.clientType,
                duration,
                proficiency,
                vitus,
                vitus?.let(ArbitrationLogMath::grade),
                record.completed,
                record.hasConflict,
            )
        }
        return ArbitrationLogHistoryReport(page, ArbitrationLogHistoryQuery.PAGE_SIZE, total, totalPages, items)
    }
}