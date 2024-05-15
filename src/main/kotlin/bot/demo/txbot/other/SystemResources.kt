package bot.demo.txbot.other

import bot.demo.txbot.genShin.apps.GachaLog
import com.mikuac.shiro.annotation.AnyMessageHandler
import com.mikuac.shiro.annotation.MessageHandlerFilter
import com.mikuac.shiro.annotation.common.Shiro
import com.mikuac.shiro.core.Bot
import com.mikuac.shiro.dto.event.message.AnyMessageEvent
import com.sun.management.OperatingSystemMXBean
import org.springframework.stereotype.Component
import java.lang.management.ManagementFactory
import java.text.DecimalFormat
import java.util.logging.Logger
import java.util.regex.Matcher

/**
 * @description: 系统资源监控
 * @author Nature Zero
 * @date 2024/5/15 下午12:44
 */

@Shiro
@Component
class SystemResources {
    private val logger: Logger = Logger.getLogger(GachaLog::class.java.getName())

    data class MonitorInfoModel(
        /**
         * 使用中的堆内存信息
         */
        var usedHeapMemoryInfo: String? = null,

        /**
         * 最大堆内存信息
         */
        var maxHeapMemoryInfo: String? = null,

        /**
         * 使用中的非堆内存信息
         */
        var usedNonHeapMemoryInfo: String? = null,

        /**
         * 最大非堆内存信息
         */
        var maxNonHeapMemoryInfo: String? = null,

        // INFO: DCTANT: 2024/3/21 计算机信息
        /**
         * 系统cpu使用率信息
         */
        var cpuLoadInfo: String? = null,

        /**
         * JVM进程 cpu使用率信息
         */
        var processCpuLoadInfo: String? = null,

        /**
         * 系统总内存信息
         */
        var totalMemoryInfo: String? = null,

        /**
         * 系统空闲内存信息
         */
        var freeMemoryInfo: String? = null,

        /**
         * 使用中的内存信息
         */
        var useMemoryInfo: String? = null,

        /**
         * 内存使用率
         */
        var memoryUseRatioInfo: String? = null,

        /**
         * 空闲交换内存信息
         */
        var freeSwapSpaceInfo: String? = null,

        /**
         * 总交换内存信息
         */
        var totalSwapSpaceInfo: String? = null,

        /**
         * 使用中交换内存信息
         */
        var useSwapSpaceInfo: String? = null,

        /**
         * 交换内存使用率信息
         */
        var swapUseRatioInfo: String? = null,

        /**
         * 系统架构
         */
        var arch: String? = null,

        /**
         * 系统名称
         */
        var name: String? = null
    )

    fun monitor(): MonitorInfoModel {
        val monitorInfoModel = MonitorInfoModel()
        val memoryMXBean = ManagementFactory.getMemoryMXBean()
        val heapMemoryUsage = memoryMXBean.heapMemoryUsage
        val nonHeapMemoryUsage = memoryMXBean.nonHeapMemoryUsage
        val decimalFormat = DecimalFormat("0.00")

        with(monitorInfoModel) {
            usedHeapMemoryInfo = formatMemorySize(heapMemoryUsage.used, decimalFormat, "MB")
            maxHeapMemoryInfo = formatMemorySize(heapMemoryUsage.max, decimalFormat, "MB")
            usedNonHeapMemoryInfo = formatMemorySize(nonHeapMemoryUsage.used, decimalFormat, "GB")
            maxNonHeapMemoryInfo = formatMemorySize(nonHeapMemoryUsage.max, decimalFormat, "GB")
        }

        val operatingSystemMXBean = ManagementFactory.getOperatingSystemMXBean()
        if (operatingSystemMXBean is OperatingSystemMXBean) {
            val totalMemorySize = operatingSystemMXBean.totalMemorySize
            val freeMemorySize = operatingSystemMXBean.freeMemorySize
            val totalSwapSpaceSize = operatingSystemMXBean.totalSwapSpaceSize
            val freeSwapSpaceSize = operatingSystemMXBean.freeSwapSpaceSize

            with(monitorInfoModel) {
                cpuLoadInfo = formatPercentage(operatingSystemMXBean.cpuLoad, decimalFormat)

                processCpuLoadInfo = formatPercentage(operatingSystemMXBean.processCpuLoad, decimalFormat)
                totalMemoryInfo = formatMemorySize(totalMemorySize, decimalFormat, "GB")
                freeMemoryInfo = formatMemorySize(freeMemorySize, decimalFormat, "GB")
                useMemoryInfo = formatMemorySize(totalMemorySize - freeMemorySize, decimalFormat, "GB")
                memoryUseRatioInfo =
                    formatPercentage((totalMemorySize - freeMemorySize).toDouble() / totalMemorySize, decimalFormat)
                totalSwapSpaceInfo = formatMemorySize(totalSwapSpaceSize, decimalFormat, "GB")
                freeSwapSpaceInfo = formatMemorySize(freeSwapSpaceSize, decimalFormat, "GB")
                useSwapSpaceInfo = formatMemorySize(totalSwapSpaceSize - freeSwapSpaceSize, decimalFormat, "GB")
                swapUseRatioInfo =
                    formatPercentage(
                        (totalSwapSpaceSize - freeSwapSpaceSize).toDouble() / totalSwapSpaceSize,
                        decimalFormat
                    )
                arch = operatingSystemMXBean.arch
                name = operatingSystemMXBean.name
            }
        }

        return monitorInfoModel
    }


    private fun formatMemorySize(size: Long, decimalFormat: DecimalFormat, unitName: String): String {
        val unitMap = mapOf(
            "MB" to 1024 * 1024.toDouble(),
            "GB" to 1024 * 1024 * 1024.toDouble()
        )
        return decimalFormat.format(size.toDouble() / unitMap[unitName]!!) + unitName
    }

    private fun formatPercentage(value: Double, decimalFormat: DecimalFormat): String {
        return decimalFormat.format(value * 100) + "%"
    }

    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "无量姬状态")
    fun deleteCache(bot: Bot, event: AnyMessageEvent?, matcher: Matcher?) {

        val monitorServer = SystemResources()

        var infoModel: MonitorInfoModel = monitorServer.monitor()
        // 多次获取保证准确性
        for (i in 0..15) {
            infoModel = monitorServer.monitor()
        }
        bot.sendMsg(event, "系统状态计算中，请稍后...", false)
        bot.sendMsg(
            event,
            "系统信息：\n" +
                    "系统架构：${infoModel.arch}\n" +
                    "系统名称：${infoModel.name}\n" +
                    "系统使用情况：\n" +
                    "CPU使用率：${infoModel.cpuLoadInfo}\n" +
                    "JVM进程CPU使用率：${infoModel.processCpuLoadInfo}\n" +
                    "系统总内存：${infoModel.totalMemoryInfo}\n" +
                    "使用中的内存：${infoModel.useMemoryInfo}\n" +
                    "内存使用率：${infoModel.memoryUseRatioInfo}\n" +
                    "使用中的堆内存：${infoModel.usedHeapMemoryInfo}\n" +
                    "最大堆内存：${infoModel.maxHeapMemoryInfo}\n" +
                    "使用中的非堆内存：${infoModel.usedNonHeapMemoryInfo}\n" +
                    "最大非堆内存：${infoModel.maxNonHeapMemoryInfo}\n" +
                    "系统总交换内存：${infoModel.totalSwapSpaceInfo}\n" +
                    "使用中的交换内存：${infoModel.useSwapSpaceInfo}\n" +
                    "交换内存使用率：${infoModel.swapUseRatioInfo}", false
        )
    }
}
