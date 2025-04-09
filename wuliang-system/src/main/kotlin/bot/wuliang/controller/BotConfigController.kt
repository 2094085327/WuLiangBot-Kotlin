package bot.wuliang.controller

import bot.wuliang.entity.BotConfigEntity
import bot.wuliang.exception.RespBean
import bot.wuliang.service.BotConfigService
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@Api(tags = ["机器人配置接口"])
@RestController
@RequestMapping("/botConfig")
class BotConfigController @Autowired constructor(private val botConfigService: BotConfigService) {
    @ApiOperation("获取机器人配置列表")
    @GetMapping("/list")
    fun getBotConfigList(): RespBean {
        return RespBean.success(botConfigService.list())
    }

    @ApiOperation("新增机器人配置")
    @PostMapping("/add")
    fun addBotConfig(botConfigEntity: BotConfigEntity): RespBean {
        botConfigService.save(botConfigEntity)
        return RespBean.success()
    }

}