package bot.demo.txbot.other

import bot.demo.txbot.common.utils.JacksonUtil
import bot.demo.txbot.common.utils.WebImgUtil
import com.mikuac.shiro.annotation.AnyMessageHandler
import com.mikuac.shiro.annotation.MessageHandlerFilter
import com.mikuac.shiro.annotation.common.Shiro
import com.mikuac.shiro.core.Bot
import com.mikuac.shiro.dto.event.message.AnyMessageEvent
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.RequestMapping
import java.util.regex.Matcher

@Shiro
@Component
@Controller
class Help(@Autowired private val webImgUtil: WebImgUtil) {
    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "(help|帮助|菜单)")
    fun help(bot: Bot, event: AnyMessageEvent?, matcher: Matcher?) {
        val helpJson = JacksonUtil.getJsonNode(HELP_JSON)
        val imageData = WebImgUtil.ImgData(
            imgName = "help-${helpJson["updateMd5"].textValue()}",
            element = "body",
            url = "http://localhost:${webImgUtil.usePort}/help"
        )
        webImgUtil.sendNewImage(
            bot,
            event,
            imageData
        )
    }

    // TODO 重载指令和更新资源后自动删除图床帮助图片然后重新生成

    @RequestMapping("/help")
    fun helpWeb(model: Model): String {
        model.addAttribute("helpList", TotalDistribution.CommandList.helpList)
        return "Other/Help"
    }
}