package bot.wuliang.controller

import bot.wuliang.entity.SysAllData
import bot.wuliang.entity.getRunTime
import bot.wuliang.entity.getStartTime
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController


/**
 * @description: 系统状态控制器
 * @author Nature Zero
 * @date 2024/12/26 20:29
 */
@RestController
@RequestMapping("/systemResources")
class SystemResourcesController {
    @GetMapping("/getSystemResources")
    fun getSystemResources(): SysAllData {
        val (cpuData, ramData, sysData, jvmData, sysFileData) = SystemResourcesMain().resourcesMain()
        return SysAllData(cpuData, ramData, sysData, jvmData, sysFileData, getStartTime(), getRunTime())
    }
}