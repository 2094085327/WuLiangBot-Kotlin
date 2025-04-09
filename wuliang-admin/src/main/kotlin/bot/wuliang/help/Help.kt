package bot.wuliang.help

import bot.wuliang.botLog.logAop.SystemLog
import bot.wuliang.botUtil.BotUtils
import bot.wuliang.config.DirectivesConfig.DIRECTIVES_KEY
import bot.wuliang.config.CommonConfig.HELP_JSON
import bot.wuliang.distribute.annotation.AParameter
import bot.wuliang.distribute.annotation.ActionService
import bot.wuliang.distribute.annotation.Executor
import bot.wuliang.imageProcess.WebImgUtil
import bot.wuliang.jacksonUtil.JacksonUtil
import bot.wuliang.redis.RedisService
import bot.wuliang.service.DirectivesService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.RequestMapping

@Component
@Controller
@ActionService
class Help @Autowired constructor(
    private val webImgUtil: WebImgUtil,
    private val redisService: RedisService,
    private val botDirectivesService: DirectivesService
) {
    @SystemLog(businessName = "获取帮助菜单")
    @AParameter
    @Executor(action = "\\b(帮助|菜单|help)\\b")
    fun help(context: BotUtils.Context) {
        val helpJson = JacksonUtil.getJsonNode(HELP_JSON)

        val imageData = WebImgUtil.ImgData(
            imgName = "help-${helpJson["updateMd5"].textValue()}",
            element = "body",
            url = "http://localhost:${webImgUtil.usePort}/help"
        )
        webImgUtil.sendNewImage(context, imageData)
    }

    @RequestMapping("/help")
    fun helpWeb(model: Model): String {
        val directivesList = redisService.getValue(DIRECTIVES_KEY)
        // TODO 移除模板 采用vue3
        model.addAttribute("directivesList", directivesList)
        return "Other/Help"
    }
}