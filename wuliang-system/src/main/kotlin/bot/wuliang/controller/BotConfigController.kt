package bot.wuliang.controller

import bot.wuliang.entity.BotConfigEntity
import bot.wuliang.exception.RespBean
import bot.wuliang.service.BotConfigService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/botConfig")
class BotConfigController @Autowired constructor(private val botConfigService: BotConfigService) {
    @GetMapping("/list")
    fun getBotConfigList(): RespBean {
        return RespBean.success(botConfigService.list())
    }

    @PostMapping("/add")
    fun addBotConfig(botConfigEntity: BotConfigEntity): RespBean {
        botConfigService.save(botConfigEntity)
        return RespBean.success()
    }

}