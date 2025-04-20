package bot.wuliang.help

import bot.wuliang.botLog.logAop.SystemLog
import bot.wuliang.botUtil.BotUtils
import bot.wuliang.config.DirectivesConfig.DIRECTIVES_KEY
import bot.wuliang.distribute.annotation.AParameter
import bot.wuliang.distribute.annotation.ActionService
import bot.wuliang.distribute.annotation.Executor
import bot.wuliang.entity.DirectivesEntity
import bot.wuliang.imageProcess.WebImgUtil
import bot.wuliang.otherUtil.OtherUtil.STConversion.toMd5
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
    private val directivesService: DirectivesService
) {
    @SystemLog(businessName = "获取帮助菜单")
    @AParameter
    @Executor(action = "\\b(帮助|菜单|help)\\b")
    fun help(context: BotUtils.Context) {
        var imgName = "help-"
        val directivesList: List<DirectivesEntity>?

        if (redisService.hasKey(DIRECTIVES_KEY)) {
            directivesList = redisService.getValueTyped<List<DirectivesEntity>>(DIRECTIVES_KEY)
        } else {
            directivesList = directivesService.selectDirectivesList(null)
            if (directivesList.isNotEmpty()) {
                redisService.setValue(DIRECTIVES_KEY, directivesList)
            }
        }

        imgName += directivesList?.toMd5() ?: ""

        val imageData = WebImgUtil.ImgData(
            imgName = imgName,
            element = "body",
            url = "http://localhost:16666/system/help"
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