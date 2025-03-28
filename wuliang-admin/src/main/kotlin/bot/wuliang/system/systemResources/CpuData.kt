package bot.wuliang.system.systemResources

data class CpuData(
    val cpuNum: Int,// 核心数
    val cpuLogicalNum:Int,// 逻辑处理器数量

    val cpuTotal: Double, // CPU总使用率
    val cpuSys: Double, // CPU系统使用率
    val cpuUsed: Double, // CPU用户使用率
    val cpuWait: Double, // CPU等待率
    val cpuFree: Double, // CPU空闲率
)

fun Double.round(places: Int): Double {
    return "%.${places}f".format(this).toDouble()
}

fun Double.mul(other: Double): Double {
    return this * other
}

fun CpuData.getTotal(): Double {
    return (((cpuTotal - cpuFree) / cpuTotal) * 100).round(2)
}

fun CpuData.getSys(): Double {
    return (cpuSys / cpuTotal).mul(100.0).round(2)
}

fun CpuData.getUsed(): Double {
    return (cpuUsed / cpuTotal).mul(100.0).round(2)
}

fun CpuData.getWait(): Double {
    return (cpuWait / cpuTotal).mul(100.0).round(2)
}

fun CpuData.getFree(): Double {
    return (cpuFree / cpuTotal).mul(100.0).round(2)
}
