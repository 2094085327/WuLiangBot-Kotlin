package bot.demo.txbot.other

import bot.demo.txbot.common.botUtil.BotUtils.ContextProvider
import bot.demo.txbot.common.utils.JacksonUtil
import bot.demo.txbot.common.utils.WebImgUtil
import com.fasterxml.jackson.databind.JsonNode
import com.mikuac.shiro.annotation.AnyMessageHandler
import com.mikuac.shiro.annotation.MessageHandlerFilter
import com.mikuac.shiro.annotation.common.Shiro
import com.mikuac.shiro.core.Bot
import com.mikuac.shiro.dto.event.message.AnyMessageEvent
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseBody
import java.util.*
import java.util.regex.Matcher

@Shiro
@Component
@Controller
class Help(@Autowired private val webImgUtil: WebImgUtil) {
    
    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "(help|帮助|菜单)")
    fun help(bot: Bot, event: AnyMessageEvent) {
        ContextProvider.initialize(event, bot)

        val helpJson = JacksonUtil.getJsonNode(HELP_JSON)
        val imageData = WebImgUtil.ImgData(
            imgName = "help-${helpJson["updateMd5"].textValue()}",
            element = "body",
            url = "http://localhost:${webImgUtil.usePort}/help"
        )
        webImgUtil.sendNewImage(imageData)
    }

    
    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "日活")
    fun daily(bot: Bot, event: AnyMessageEvent) {
        ContextProvider.initialize(event, bot)

        val imageData = WebImgUtil.ImgData(
            imgName = "dailyActive-${UUID.randomUUID()}",
            element = "body",
            url = "http://localhost:${webImgUtil.usePort}/dailyActive"
        )
        webImgUtil.sendNewImage(imageData)
        webImgUtil.deleteImg(imageData)
    }

    @RequestMapping("/help")
    fun helpWeb(model: Model): String {
        model.addAttribute("helpList", TotalDistribution.CommandList.helpList)
        return "Other/Help"
    }

    @ResponseBody
    @GetMapping("/dailyJson")
    fun dailyJson(): JsonNode {
        val helpJson = JacksonUtil.getJsonNode(DAILY_ACTIVE_PATH)

        return helpJson
    }

    @RequestMapping("/dailyActive")
    fun dailyActive(model: Model): String {
        return "Other/DailyActive"
    }
}