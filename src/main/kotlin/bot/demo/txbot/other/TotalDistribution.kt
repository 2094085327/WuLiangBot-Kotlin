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
        val commandList: List<String> get() = _commandList.toList()
        val commands: List<String> get() = _commands.toList()

        init {
            reloadCommands()
        }

        fun reloadCommands() {
            _commandList.clear()
            val helpJson = JacksonUtil.getJsonNode("resources/others/help.json")
            helpJson.fieldNames().forEach { fieldName ->
                _commandList.add(helpJson[fieldName]["regex"].textValue())
                _commands.add(helpJson[fieldName]["command"].textValue())
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
            likedMatch.forEach {
                stringBuilder.append("$it\n")
            }
            bot.sendMsg(
                event, "你输入了未知指令呢，请仔细检查一下,也许你想找的是:\n\n${stringBuilder}\n" +
                        "/help -获取帮助信息\n" +
                        "/历史记录 -获取原神抽卡记录\n" +
                        "/重开 -人生重开小游戏\n" +
                        "/裂缝 -Warframe 裂缝查询", false
            )
        }
    }
}
