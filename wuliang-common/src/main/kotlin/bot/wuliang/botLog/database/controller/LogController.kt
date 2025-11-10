package bot.wuliang.botLog.database.controller

import bot.wuliang.botLog.database.service.impl.LogServiceImpl
import bot.wuliang.botLog.database.entity.LogEntity
import bot.wuliang.core.page.TableDataInfo
import bot.wuliang.utils.PageUtils
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController


/**
 * @description: 日志控制器
 * @author Nature Zero
 * @date 2024/12/30 19:39
 */
@Api(tags = ["日志接口"])
@RestController
@RequestMapping("/log")
class LogController {
    @Autowired
    private lateinit var logService: LogServiceImpl

    @ApiOperation("获取指令列表")
    @GetMapping("/getLog")
    fun getLog(logEntity: LogEntity): TableDataInfo {
        val startPage = PageUtils().startPage<LogEntity>()
        return TableDataInfo().build(logService.selectList(startPage, logEntity))
    }
}