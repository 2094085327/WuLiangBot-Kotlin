package bot.demo.txbot.other.systemResources

data class RamData(
    val ramTotal: Double, // 总内存
    val ramUsed: Double, // 已使用内存
    val ramFree: Double, // 空闲内存
)

fun RamData.getTotal(): Double {
    return ramTotal.divBy(1024.0 * 1024.0 * 1024.0, 2)
}

fun RamData.getUsed(): Double {
    return ramUsed.divBy(1024.0 * 1024.0 * 1024.0, 2)
}

fun RamData.getFree(): Double {
    return ramFree.divBy(1024.0 * 1024.0 * 1024.0, 2)
}

fun RamData.getUsage(): Double {
    return ((ramUsed / ramTotal) * 100).divBy(1.0, 4)
}
