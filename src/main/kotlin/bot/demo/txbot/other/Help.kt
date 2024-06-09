package bot.demo.txbot.other

import bot.demo.txbot.common.utils.JacksonUtil
import bot.demo.txbot.common.utils.WebImgUtil
import com.mikuac.shiro.annotation.AnyMessageHandler
import com.mikuac.shiro.annotation.MessageHandlerFilter
import com.mikuac.shiro.annotation.common.Shiro
import com.mikuac.shiro.core.Bot
import com.mikuac.shiro.dto.event.message.AnyMessageEvent
import org.springframework.stereotype.Component
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.RequestMapping
import java.io.File
import java.util.regex.Matcher

@Shiro
@Component
@Controller
class Help {
    companion object {
        var helpList = mutableListOf<HelpData>()
    }

    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "help")
    fun help(bot: Bot, event: AnyMessageEvent?, matcher: Matcher?) {
        val helpImageName = "help"

        val folderPath = IMG_CACHE_PATH
        val folder = File(folderPath)

        val matchingFileName = folder.listFiles()?.firstOrNull {  it.nameWithoutExtension.contains(helpImageName) && it.extension == "tmp" }?.name

        val webImgUtil = WebImgUtil()
        if (matchingFileName != null) {
            webImgUtil.sendCachedImage(bot, event, matchingFileName)
            return
        }

        readHelp()
        val imageData = WebImgUtil.ImgData(
            imgName = "help",
            element = "body",
            url = "http://localhost:${WebImgUtil.usePort}/help"
        )
        webImgUtil.sendNewImage(
            bot,
            event,
            imageData
        )
        helpList = mutableListOf()
    }

    @RequestMapping("/help")
    fun helpWeb(model: Model): String {
        model.addAttribute("helpList", helpList)
        return "Other/Help"
    }

    data class HelpData(
        var command: String? = null,
        var description: String? = null
    )

    fun readHelp(): MutableList<HelpData> {
        val helpJson = JacksonUtil.getJsonNode("resources/others/help.json")
        val fieldNames = helpJson.fieldNames()
        while (fieldNames.hasNext()) {
            val next = fieldNames.next()
            helpList.add(
                HelpData(
                    command = helpJson[next]["command"].textValue(),
                    description = helpJson[next]["description"].textValue()
                )
            )
        }

        return helpList
    }
}