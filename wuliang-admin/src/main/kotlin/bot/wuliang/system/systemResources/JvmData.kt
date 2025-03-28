package bot.wuliang.system.systemResources

import java.lang.management.ManagementFactory
import java.util.*

data class JvmData(
    val jvmTotal: Double,// 当前JVM占用的内存总数(M)
    val jvmMax: Double,// JVM最大可用内存(M)
    val jvmFree: Double,// JVM空闲内存(M)
    val jvmVersion: String,// JDK版本
    val jvmHome: String,// JDK路径
)

fun Double.divBy(value: Double, scale: Int): Double {
    return String.format("%.${scale}f", this / value).toDouble()
}

fun Double.mulBy(value: Double): Double {
    return this * value
}

fun JvmData.getTotal(): Double {
    return jvmTotal.divBy(1024.0 * 1024.0, 2)
}

fun JvmData.getMax(): Double {
    return jvmMax.divBy(1024.0 * 1024.0, 2)
}

fun JvmData.getFree(): Double {
    return jvmFree.divBy(1024.0 * 1024.0, 2)
}

fun JvmData.getUsed(): Double {
    return (jvmTotal - jvmFree).divBy(1024.0 * 1024.0, 2)
}

fun JvmData.getUsage(): Double {
    return ((jvmTotal - jvmFree) / jvmTotal).mulBy(100.0).divBy(1.0, 4)
}

fun getName(): String {
    return ManagementFactory.getRuntimeMXBean().vmName
}

fun getStartTime(): String {
    val time = ManagementFactory.getRuntimeMXBean().startTime
    val date = Date(time)
    return java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(date)
}

fun getRunTime(): String {
    val time = ManagementFactory.getRuntimeMXBean().startTime
    val startDate = Date(time)
    val runMS = Date().time - startDate.time

    val nd = 1000 * 24 * 60 * 60
    val nh = 1000 * 60 * 60
    val nm = 1000 * 60

    val day = runMS / nd
    val hour = runMS % nd / nh
    val min = runMS % nd % nh / nm
    return "${day}天${hour}小时${min}分钟"
}