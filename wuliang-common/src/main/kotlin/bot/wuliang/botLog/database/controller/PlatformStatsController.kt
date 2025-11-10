package bot.wuliang.botLog.database.controller

import bot.wuliang.botLog.database.service.PlatformStatsService
import bot.wuliang.exception.RespBean
import io.swagger.annotations.Api
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@Api(tags = ["消息统计"])
@RestController
@RequestMapping("/platformStats")
class PlatformStatsController(@Autowired private val platformStatsService: PlatformStatsService) {

    /**
     * 获取对应时间范围的消息统计数据
     *
     * @param offset 时间偏移量，单位为秒
     */
    @GetMapping("/getStats")
    fun getStats(@RequestParam("offset", required = false) offset: Int?): RespBean? {
        val actualOffset = offset ?: 86400
        // 从数据库中查询对应时间范围的消息统计数据
        val platformStats = platformStatsService.getPlatformStatsWithOffset(actualOffset)
        return RespBean.success(platformStats)
    }

    /**
     * 获取对应时间范围的消息统计数据
     *
     */
    @GetMapping("/getAllMsgCount")
    fun getAllMsgCount(): RespBean? {
        // 从数据库中查询对应时间范围的消息统计数据
        val count = platformStatsService.getAllMsgCount()
        return RespBean.success(count)
    }
}