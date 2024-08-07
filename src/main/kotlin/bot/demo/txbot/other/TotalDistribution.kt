package bot.demo.txbot.other

import bot.demo.txbot.common.database.template.TemplateService
import bot.demo.txbot.common.utils.JacksonUtil
import bot.demo.txbot.common.utils.LoggerUtils.logInfo
import bot.demo.txbot.common.utils.OtherUtil
import bot.demo.txbot.common.utils.OtherUtil.STConversion.toMd5
import bot.demo.txbot.common.utils.WebImgUtil
import bot.demo.txbot.other.TotalDistribution.CommandList.helpMd5
import bot.demo.txbot.other.TotalDistribution.CommandList.lastHelpMd5
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.mikuac.shiro.annotation.AnyMessageHandler
import com.mikuac.shiro.annotation.MessageHandlerFilter
import com.mikuac.shiro.annotation.common.Shiro
import com.mikuac.shiro.core.Bot
import com.mikuac.shiro.dto.event.message.AnyMessageEvent
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.io.File
import java.util.*
import java.util.regex.Matcher


/**
 * @description: 总分发方法，将指令分发至各类
 * @author Nature Zero
 * @date 2024/7/13 下午8:41
 */
@Shiro
@Component
class TotalDistribution(
    @Autowired private val templateService: TemplateService,
    @Autowired private val webImgUtil: WebImgUtil
) {

    object CommandList {
        private val _commandList = mutableListOf<String>()
        private val _commands = mutableListOf<String>()
        private val _commandDescription = mutableListOf<String>()
        var CHECKCOMMAND = true
        var helpList = listOf<HelpData>()
            private set
        var helpMd5: String? = null
        var lastHelpMd5: String? = null


        val commandList: List<String> get() = _commandList.toList()
        val commands: List<String> get() = _commands.toList()

        data class HelpData(
            var command: String? = null,
            var description: String? = null
        )

        init {
            reloadCommands()
        }

        fun reloadCommands() {
            _commandList.clear()
            _commands.clear()
            _commandDescription.clear()

            val helpJson = JacksonUtil.getJsonNode(HELP_JSON)
            val commands = helpJson["commendList"].first()
            CHECKCOMMAND = helpJson["checkCmd"].booleanValue()

            // 遍历 help.json 中的指令列表和正则匹配
            commands.fieldNames().forEach { fieldName ->
                _commandList.add(commands[fieldName]["regex"].textValue())
                _commands.add(commands[fieldName]["command"].textValue())
                _commandDescription.add(commands[fieldName]["description"].textValue())
            }

            helpList = List(_commandList.size) { index ->
                HelpData(command = _commands[index], description = _commandDescription[index])
            }
            lastHelpMd5 = helpJson["updateMd5"].textValue()
            helpMd5 = helpJson["commendList"].toString().toMd5()
            if (helpMd5 != lastHelpMd5) {
                helpJson as ObjectNode
                helpJson.put("updateMd5", helpMd5)
            }
            val mapper = ObjectMapper()
            mapper.writeValue(File(HELP_JSON), helpJson)
        }
    }

    fun getDescriptionsForCommands(commands: List<String>, helpList: List<CommandList.HelpData>): List<String> {
        return commands.map { command ->
            helpList.find { it.command == command }?.description
                ?: "这个指令没有什么描述呢"
        }
    }

    private fun updateParam(param: ObjectNode, key: String, matchedCommands: List<String>, descriptions: List<String>) {
        val index = key.last().digitToIntOrNull()?.minus(1) ?: return
        val value = when {
            key.startsWith("data") -> matchedCommands.getOrNull(index)
            key.startsWith("description") -> descriptions.getOrNull(index)
            else -> null
        }
        value?.let { param.putArray("values").add(it) }
    }

    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "重载指令")
    fun reloadConfig(bot: Bot, event: AnyMessageEvent, matcher: Matcher) {
        CommandList.reloadCommands()
        bot.sendMsg(event, "指令列表已重载", false)
        logInfo("指令列表已重载")
        val imageData = WebImgUtil.ImgData(
            imgName = "help-$lastHelpMd5",
            element = "body",
            url = "http://localhost:${webImgUtil.usePort}/help"
        )
        if (lastHelpMd5 != helpMd5) {
            webImgUtil.deleteImgByQiNiu(imageData)
            logInfo("帮助缓存已删除")
        }
        lastHelpMd5 = helpMd5
    }

    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "(.*)")
    fun totalDistribution(bot: Bot, event: AnyMessageEvent, matcher: Matcher) {
        val match = matcher.group(1)
        val matchedCommand = CommandList.commandList.firstOrNull { it.toRegex().matches(match) }
        if (matchedCommand == null) {
            val matchedCommands = OtherUtil().findMatchingStrings(match, CommandList.commands)
            val descriptions = getDescriptionsForCommands(matchedCommands, CommandList.helpList)

            // 检查帮助配置文件中的 checkCmd 配置，true 则开启未知指令拦截并发送帮助信息
            val template = templateService.searchByBotIdAndTemplateName(bot.selfId, "help")

            if (!CommandList.CHECKCOMMAND && template == null) return
            val templateJson = template!!.content?.let { JacksonUtil.readTree(it) } as ObjectNode
            val paramsArray = templateJson.with("markdown").withArray("params") as ArrayNode

            paramsArray.forEach { param ->
                param as ObjectNode
                val key = param.get("key").asText()
                updateParam(param, key, matchedCommands, descriptions)
            }

            val encodedInputBase64 =
                Base64.getEncoder().encodeToString(templateJson.toString().toByteArray())
            bot.sendMsg(event, "[CQ:markdown,data=base64://$encodedInputBase64]", false)

            // TODO 临时补丁，被动Md被修复 先直发一下进行兜底回复 未来增加md开关
            bot.sendMsg(event, "未知指令，你可能在找这些指令：$matchedCommands", false)
        }
    }
}
