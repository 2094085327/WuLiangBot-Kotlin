package bot.wuliang.controller

import bot.wuliang.botUtil.BotUtils
import bot.wuliang.distribute.annotation.AParameter
import bot.wuliang.distribute.annotation.ActionService
import bot.wuliang.distribute.annotation.Executor
import bot.wuliang.entity.*
import org.springframework.stereotype.Component
import oshi.SystemInfo
import oshi.hardware.CentralProcessor
import oshi.hardware.CentralProcessor.TickType
import oshi.hardware.GlobalMemory
import oshi.hardware.HardwareAbstractionLayer
import oshi.software.os.FileSystem
import oshi.software.os.OSFileStore
import oshi.software.os.OperatingSystem
import oshi.util.Util


/**
 * @description: 系统资源数据
 * @author Nature Zero
 * @date 2024/5/19 下午3:58
 */
@ActionService
@Component
class SystemResourcesMain {
    data class SystemResources(
        val cpuData: CpuData,
        val ramData: RamData,
        val sysData: SysData,
        val jvmData: JvmData,
        val sysFileData: MutableList<SysFileData>
    )

    /**
     * 获取系统资源信息
     */
    fun resourcesMain(): SystemResources {

        val si = SystemInfo()
        val hal: HardwareAbstractionLayer = si.hardware

        val cpuData = setCpuInfo(hal.processor)

        val ramData = setRamInfo(hal.memory)

        val sysData = setSysInfo()

        val jvmData = setJvmInfo()

        val sysFileData = setSysFiles(si.operatingSystem)
        return SystemResources(cpuData, ramData, sysData, jvmData, sysFileData)
    }

    /**
     * 设置CPU信息
     *
     * @param processor CPU信息
     */
    fun setCpuInfo(processor: CentralProcessor): CpuData {
        // CPU信息
        val prevTicks = processor.systemCpuLoadTicks
        Util.sleep(1000)
        val ticks = processor.systemCpuLoadTicks
        val nice = ticks[TickType.NICE.index] - prevTicks[TickType.NICE.index]
        val irq = ticks[TickType.IRQ.index] - prevTicks[TickType.IRQ.index]
        val softIrq = ticks[TickType.SOFTIRQ.index] - prevTicks[TickType.SOFTIRQ.index]
        val steal = ticks[TickType.STEAL.index] - prevTicks[TickType.STEAL.index]
        val cpuSys = (ticks[TickType.SYSTEM.index] - prevTicks[TickType.SYSTEM.index]).toDouble()
        val cpuUsed = (ticks[TickType.USER.index] - prevTicks[TickType.USER.index]).toDouble()
        val cpuWait = (ticks[TickType.IOWAIT.index] - prevTicks[TickType.IOWAIT.index]).toDouble()
        val cpuFree = (ticks[TickType.IDLE.index] - prevTicks[TickType.IDLE.index]).toDouble()
        val cpuTotal = nice + irq + softIrq + steal + cpuSys + cpuUsed + cpuWait + cpuFree
        val cpuData = CpuData(
            cpuTotal = cpuTotal,
            cpuSys = cpuSys,
            cpuNum = processor.physicalProcessorCount,
            cpuLogicalNum = processor.logicalProcessorCount,
            cpuUsed = cpuUsed,
            cpuFree = cpuFree,
            cpuWait = cpuWait,
        )
        return cpuData
    }

    /**
     * 设置内存信息
     *
     * @param memory 内存信息
     */
    fun setRamInfo(memory: GlobalMemory): RamData {
        val ram = RamData(
            ramTotal = memory.total.toDouble(),
            ramUsed = memory.total.toDouble() - memory.available.toDouble(),
            ramFree = memory.available.toDouble(),
        )

        return ram
    }

    fun setSysInfo(): SysData {
        val props = System.getProperties()

        val sys = SysData(
            osName = props.getProperty("os.name"),
            osArch = props.getProperty("os.arch"),
            userDir = props.getProperty("user.dir")
        )
        return sys
    }

    /**
     * 设置Java虚拟机
     */
    fun setJvmInfo(): JvmData {
        val props = System.getProperties()
        val jvm = JvmData(
            jvmTotal = Runtime.getRuntime().totalMemory().toDouble(),
            jvmMax = Runtime.getRuntime().maxMemory().toDouble(),
            jvmFree = Runtime.getRuntime().freeMemory().toDouble(),
            jvmVersion = props.getProperty("java.version"),
            jvmHome = props.getProperty("java.home")
        )

        return jvm
    }

    /**
     * 设置磁盘信息
     *
     * @param os 操作系统
     */
    fun setSysFiles(os: OperatingSystem): MutableList<SysFileData> {
        val fileSystem: FileSystem = os.fileSystem
        val fsArray: MutableList<OSFileStore> = fileSystem.fileStores
        val sysFileList: MutableList<SysFileData> = mutableListOf()
        for (fs in fsArray) {
            val free = fs.usableSpace
            val total = fs.totalSpace
            val used = total - free
            val sysFile = SysFileData(
                dirName = fs.mount,
                sysTypeName = fs.type,
                typeName = fs.name,
                fileTotal = total,
                fileFree = free,
                fileUsed = used,
                fileUsage = (used / total.toDouble() * 100).round(2)
            )
            sysFileList.add(sysFile)
        }
        return sysFileList
    }

    /**
     * 字节转换
     *
     * @param size 字节大小
     * @return 转换后值
     */
    private fun convertFileSize(size: Long): String {
        val kb: Long = 1024
        val mb = kb * 1024
        val gb = mb * 1024
        if (size >= gb) {
            return String.format("%.1f GB", size.toFloat() / gb)
        } else if (size >= mb) {
            val f = size.toFloat() / mb
            return String.format(if (f > 100) "%.0f MB" else "%.1f MB", f)
        } else if (size >= kb) {
            val f = size.toFloat() / kb
            return String.format(if (f > 100) "%.0f KB" else "%.1f KB", f)
        } else {
            return String.format("%d B", size)
        }
    }


    @AParameter
    @Executor(action = "无量姬状态")
    fun sendSystemResources(context: BotUtils.Context) {
        val (cpuData, ramData, sysData, jvmData, sysFileData) = resourcesMain()

        context.sendMsg("系统状态计算中，请稍后...")
        val sysFileString: String = sysFileData.joinToString(separator = "\n") {
            "盘符: ${it.dirName}\n" +
                    "盘符类型: ${it.sysTypeName}\n" +
                    "磁盘名: ${it.typeName}\n" +
                    "总大小: ${convertFileSize(it.fileTotal)}\n" +
                    "剩余大小: ${convertFileSize(it.fileFree)}\n" +
                    "已用大小: ${convertFileSize(it.fileUsed)}\n" +
                    "空间使用率: ${it.fileUsage}%\n"
        }

        context.sendMsg(

            "Cpu总使用率: ${cpuData.getTotal()}%\n" +
                    "Cpu核心数: ${cpuData.cpuNum}\n" +
                    "Cpu逻辑处理器数: ${cpuData.cpuLogicalNum}\n" +
                    "Cpu系统使用率: ${cpuData.getSys()}%\n" +
                    "Cpu用户使用率: ${cpuData.getUsed()}%\n" +
                    "Cpu等待率: ${cpuData.getWait()}%\n" +
                    "Cpu空闲: ${cpuData.getFree()}%\n\n" +
                    "总内存: ${ramData.getTotal()}G\n" +
                    "已使用内存: ${ramData.getUsed()}G\n" +
                    "空闲内存: ${ramData.getFree()}G\n" +
                    "内存使用率: ${ramData.getUsage()}%\n\n" +
                    "操作系统: ${sysData.osName}\n" +
                    "系统架构: ${sysData.osArch}\n" +
                    "用户目录: ${sysData.userDir}\n\n" +
                    "JVM总内存: ${jvmData.getTotal()}M\n" +
                    "JVM最大内存: ${jvmData.getMax()}M\n" +
                    "JVM空闲内存: ${jvmData.getFree()}M\n" +
                    "JVM使用内存: ${jvmData.getUsed()}M\n" +
                    "JVM内存使用率: ${jvmData.getUsage()}%\n" +
                    "JVM版本: ${jvmData.jvmVersion}\n" +
                    "JVM路径: ${jvmData.jvmHome}\n\n" +
                    "机器人运行开始时间: ${getStartTime()}\n" +
                    "已运行: ${getRunTime()}\n" +
                    sysFileString
        )
    }
}