package bot.demo.txbot.other

import bot.demo.txbot.common.utils.JacksonUtil
import bot.demo.txbot.common.utils.OtherUtil
import com.mikuac.shiro.annotation.AnyMessageHandler
import com.mikuac.shiro.annotation.MessageHandlerFilter
import com.mikuac.shiro.annotation.common.Shiro
import com.mikuac.shiro.core.Bot
import com.mikuac.shiro.dto.event.message.AnyMessageEvent
import org.springframework.stereotype.Component
import pers.wuliang.robot.common.utils.LoggerUtils.logInfo
import java.util.*
import java.util.regex.Matcher


/**
 * @description: 总分发方法，将指令分发至各类
 * @author Nature Zero
 * @date 2024/7/13 下午8:41
 */
@Shiro
@Component
class TotalDistribution {
    object CommandList {
        private val _commandList = mutableListOf<String>()
        private val _commands = mutableListOf<String>()
        private val _commandDescription = mutableListOf<String>()
        var CHECKCOMMEND = true
        val commandList: List<String> get() = _commandList.toList()
        val commands: List<String> get() = _commands.toList()
        val helpList: List<HelpData> get() = List(_commandList.size) { index ->
            HelpData(command = _commands[index], description = _commandDescription[index])
        }

        data class HelpData(
            var command: String? = null,
            var description: String? = null
        )

        init {
            reloadCommands()
        }

        fun reloadCommands() {
            _commandList.clear()
            val helpJson = JacksonUtil.getJsonNode("resources/others/help.json")
            val commands = helpJson["commendList"].first()
            CHECKCOMMEND = helpJson["checkCmd"].booleanValue()

            // 遍历help.json中的指令列表和正则匹配
            commands.fieldNames().forEach { fieldName ->
                _commandList.add(commands[fieldName]["regex"].textValue())
                _commands.add(commands[fieldName]["command"].textValue())
                _commandDescription.add(commands[fieldName]["description"].textValue())
            }
        }
    }

    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "重载指令")
    fun reloadConfig(bot: Bot, event: AnyMessageEvent, matcher: Matcher) {
        CommandList.reloadCommands()
        logInfo("指令列表已重载")
        bot.sendMsg(event, "指令列表已重载", false)
    }

    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "(.*)")
    fun totalDistribution(bot: Bot, event: AnyMessageEvent, matcher: Matcher) {
        val match = matcher.group(1)
        val matchedCommand = CommandList.commandList.firstOrNull { it.toRegex().matches(match) }
        if (matchedCommand == null) {
            val likedMatch = OtherUtil().findMatchingStrings(match, CommandList.commands)
            val stringBuilder = StringBuilder()
            likedMatch.forEach { stringBuilder.append("$it\n") }

            // 检查帮助配置文件中的checkCmd配置，true则开启未知指令拦截并发送帮助信息
            if (CommandList.CHECKCOMMEND) {
                val jsonStr = "{\"markdown\":{\"custom_template_id\":\"102076980_1721042124\",\"params\":[{\"key\":\"title_text\",\"values\":[\"∠( ᐛ 」∠)＿没找到这个指令呢,但是无量姬找到了这个:\"]},{\"key\":\"image\",\"values\":[\"http://sgtk5pzgv.hn-bkt.clouddn.com/img/wuliang.png\"]},{\"key\":\"data1\",\"values\":[\"菜单1\"]},{\"key\":\"description1\",\"values\":[\"这是菜单1\"]},{\"key\":\"data2\",\"values\":[\"段落2\"]},{\"key\":\"description2\",\"values\":[\"这是菜单2\"]},{\"key\":\"data3\",\"values\":[\"简介\"]},{\"key\":\"description3\",\"values\":[\"这是简介\"]},{\"key\":\"data4\",\"values\":[\"在这个子频道非常开心\"]},{\"key\":\"description4\",\"values\":[\"这是链接介绍\"]}]}}"

                val encodedInputBase64 = Base64.getEncoder().encodeToString(jsonStr.toByteArray())
                bot.sendMsg(event, "[CQ:markdown,data=base64://$encodedInputBase64]", false)
            }
//                bot.sendMsg(
//                event, "\n你输入了未知指令呢，请仔细检查一下,也许你想找的是:\n\n${stringBuilder}\n" +
//                        "/help -获取帮助信息\n" +
//                        "/历史记录 -获取原神抽卡记录\n" +
//                        "/重开 -人生重开小游戏\n" +
//                        "/裂缝 -Warframe 裂缝查询", false
//            )
        }
    }
}
