package bot.wuliang.command

import bot.wuliang.adapter.context.ExecutionContext
import bot.wuliang.distribute.annotation.AParameter
import bot.wuliang.distribute.annotation.ActionService
import bot.wuliang.distribute.annotation.Executor
import bot.wuliang.logAop.SystemLog
import bot.wuliang.translation.PublicExportService
import bot.wuliang.utils.TimeUtils.formatDuration
import bot.wuliang.worldstate.WikiWeaponRotationSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Wiki 武器轮换指令，沿用独立于官方世界状态的数据源与缓存。 */
@Component
@ActionService
class WeaponRotationCommands(
    private val translations: PublicExportService,
    private val wikiWeaponRotations: WikiWeaponRotationSource,
) {

    /** 读取 Wiki 信条与终幕武器轮换，生成包含来源和更新状态的图片并发送。 */
    @SystemLog(businessName = "获取信条/终幕武器轮换信息")
    @AParameter
    @Executor(action = "\\b(佩兰|信条|终幕|信条轮换|终幕轮换)\\b")
    suspend fun weaponRotation(context: ExecutionContext) {
        val reports = withContext(Dispatchers.IO) { wikiWeaponRotations.reports() }
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("Asia/Shanghai"))
        val elements = mapOf(
            "Electricity" to "电击", "Cold" to "冰冻", "Heat" to "火焰", "Impact" to "冲击",
            "Magnetic" to "磁力", "Radiation" to "辐射", "Toxin" to "毒素"
        )
        val message = reports.joinToString("\n\n") { report ->
            buildString {
                appendLine("${report.title}轮换${report.batch?.let { " · 批次 $it" }.orEmpty()}")
                appendLine("下次刷新：${formatter.format(Instant.ofEpochMilli(report.expiry))}")
                appendLine(
                    "剩余：${
                        formatDuration(
                            Duration.ofMillis(
                                (report.expiry - System.currentTimeMillis()).coerceAtLeast(
                                    0
                                )
                            )
                        )
                    }"
                )
                report.wikiUpdatedAt?.let { appendLine("Wiki 修订：${formatter.format(Instant.ofEpochMilli(it))}") }
                appendLine(report.note)
                report.weapons.forEach { offer ->
                    val name = if (translations.ready) translations.name(offer.name) else offer.name
                    val bonus = java.math.BigDecimal.valueOf(offer.bonus).stripTrailingZeros().toPlainString()
                    appendLine("$name · ${elements[offer.element] ?: offer.element} +$bonus%")
                }
            }
        }
        context.sender.sendText(message)
    }
}