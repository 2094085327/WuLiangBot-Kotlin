package bot.demo.txbot.other

import bot.demo.txbot.common.botUtil.BotUtils.Context
import bot.demo.txbot.common.exception.RespBean
import bot.demo.txbot.common.utils.JacksonUtil
import bot.demo.txbot.common.utils.WebImgUtil
import bot.demo.txbot.other.TotalDistribution.CommandList.commandConfig
import bot.demo.txbot.other.distribute.annotation.AParameter
import bot.demo.txbot.other.distribute.annotation.ActionService
import bot.demo.txbot.other.distribute.annotation.Executor
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseBody
import java.util.*

@Component
@Controller
@ActionService
class Help(@Autowired private val webImgUtil: WebImgUtil, @Autowired private val totalDistribution: TotalDistribution) {
    @AParameter
    @Executor(action = "\\b(帮助|菜单|help)\\b")
    fun help(context: Context) {
        val helpJson = JacksonUtil.getJsonNode(HELP_JSON)
        val imageData = WebImgUtil.ImgData(
            imgName = "help-${helpJson["updateMd5"].textValue()}",
            element = "body",
            url = "http://localhost:${webImgUtil.usePort}/help"
        )
        webImgUtil.sendNewImage(context, imageData)
    }

    @AParameter
    @Executor(action = "日活")
    fun daily(context: Context) {
        totalDistribution.saveActiveLog()
        val imageData = WebImgUtil.ImgData(
            imgName = "dailyActive-${UUID.randomUUID()}",
            element = "body",
            url = "http://localhost:${webImgUtil.usePort}/dailyActive"
        )
        webImgUtil.sendNewImage(context, imageData)
        webImgUtil.deleteImg(imageData)
    }

    @RequestMapping("/help")
    fun helpWeb2(model: Model): String {
        model.addAttribute("commandConfig", commandConfig)
        return "Other/Help"
    }

    @ResponseBody
    @GetMapping("/dailyJson")
    fun dailyJson(): RespBean {
        val helpJson = JacksonUtil.getJsonNode(DAILY_ACTIVE_PATH)
        return RespBean.success(helpJson)
    }

    @RequestMapping("/dailyActive")
    fun dailyActive(model: Model): String {
        return "Other/DailyActive"
    }
}