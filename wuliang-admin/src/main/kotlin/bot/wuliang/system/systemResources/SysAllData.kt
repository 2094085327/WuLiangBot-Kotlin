package bot.wuliang.system.systemResources

data class SysAllData(
    val cpuData: CpuData,
    val ramData: RamData,
    val sysData: SysData,
    val jvmData: JvmData,
    val sysFileData: MutableList<SysFileData>,
    val startDate: String,// 启动时间
    val runTime: String// 运行时间
)
