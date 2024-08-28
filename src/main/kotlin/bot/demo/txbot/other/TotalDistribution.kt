package bot.demo.txbot.other

import bot.demo.txbot.TencentBotKotlinApplication
import bot.demo.txbot.common.botUtil.BotUtils.ContextProvider
import bot.demo.txbot.common.database.template.TemplateService
import bot.demo.txbot.common.utils.JacksonUtil
import bot.demo.txbot.common.utils.LoggerUtils.logInfo
import bot.demo.txbot.common.utils.OtherUtil
import bot.demo.txbot.common.utils.OtherUtil.STConversion.toMd5
import bot.demo.txbot.common.utils.WebImgUtil
import bot.demo.txbot.other.TotalDistribution.CommandList.dailyActiveJson
import bot.demo.txbot.other.TotalDistribution.CommandList.helpMd5
import bot.demo.txbot.other.TotalDistribution.CommandList.lastHelpMd5
import bot.demo.txbot.other.distribute.actionConfig.ActionFactory
import bot.demo.txbot.other.distribute.actionConfig.Addition
import bot.demo.txbot.other.distribute.annotation.AParameter
import bot.demo.txbot.other.distribute.annotation.ActionService
import bot.demo.txbot.other.distribute.annotation.Executor
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.readValue
import com.mikuac.shiro.annotation.AnyMessageHandler
import com.mikuac.shiro.annotation.MessageHandlerFilter
import com.mikuac.shiro.annotation.common.Order
import com.mikuac.shiro.annotation.common.Shiro
import com.mikuac.shiro.core.Bot
import com.mikuac.shiro.dto.event.message.AnyMessageEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.event.ContextClosedEvent
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.regex.Matcher
import javax.annotation.PostConstruct


/**
 * @description: 总分发方法，将指令分发至各类
 * @author Nature Zero
 * @date 2024/7/13 下午8:41
 */
@Shiro
@Component
@ActionService
class TotalDistribution(
    @Autowired private val templateService: TemplateService,
    @Autowired private val webImgUtil: WebImgUtil,
    @Autowired private val actionFactory: ActionFactory,
    @Autowired private val addition: Addition
) {

    private val scope = CoroutineScope(Dispatchers.Default)
    private val mapper = ObjectMapper() // 获取 ObjectMapper 对象

    object CommandList {
        private val _commandList = mutableListOf<String>()
        private val _commands = mutableListOf<String>()
        private val _commandDescription = mutableListOf<String>()
        var CHECK_COMMAND = true
        var helpList = listOf<HelpData>()
            private set
        var helpMd5: String? = null
        var lastHelpMd5: String? = null


        val commandList: List<String> get() = _commandList.toList()
        val commands: List<String> get() = _commands.toList()

        var dailyActiveJson = JacksonUtil.getJsonNode(DAILY_ACTIVE_PATH)

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
            CHECK_COMMAND = helpJson["checkCmd"].booleanValue()

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

    @Scheduled(cron = "0 0 0 * * ?")
    fun creatTodayActiveData() {
        if (dailyActiveJson is ObjectNode) {
            val data = dailyActiveJson["data"] as ArrayNode
            val lastData = data.last() as ObjectNode
            val currentTime =
                LocalDateTime.now(ZoneId.of("Asia/Shanghai")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
            if (lastData["date"].textValue() != currentTime) {
                lastData.put("dailyActiveUsers", dailyActiveJson["users"].size())
                if (lastData["dailyActiveUsers"].intValue() != 0) {
                    (dailyActiveJson["users"] as ArrayNode).removeAll()
                }
                val nodeFactory = mapper.nodeFactory // 获取 JsonNodeFactory 对象
                val newData = ObjectNode(nodeFactory) // 使用 JsonNodeFactory 创建 ObjectNode
                newData.put("date", currentTime)
                newData.put("dailyActiveUsers", 0)
                newData.put("totalUpMessages", 0)
                data.add(newData)
                mapper.writeValue(File(DAILY_ACTIVE_PATH), dailyActiveJson)
                logInfo("创建今日日活")
            }
        }
    }

    @Scheduled(cron = "0 1 * * * ?")
    fun saveActiveLog() {
        mapper.writeValue(File(DAILY_ACTIVE_PATH), dailyActiveJson)
    }

    @PostConstruct
    fun creatDailyActiveFile() {
        scope.launch {
            val file = File(DAILY_ACTIVE_PATH)
            if (!file.exists()) {
                file.createNewFile()
                // 当前日期
                val currentTime =
                    LocalDateTime.now(ZoneId.of("Asia/Shanghai")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
                val json =
                    """{"data":[{"date":"$currentTime","dailyActiveUsers":0,"totalUpMessages":0}],"users":[]}""".trimIndent()
                file.writeText(json)
                logInfo("日活日志文件缺失，已自动创建")
            }
            creatTodayActiveData()
        }
    }

    @EventListener
    fun endEventListenerShutdown(event: ContextClosedEvent) {
        // 保存日活日志
        saveActiveLog()
        webImgUtil.shutdown()
        logInfo("程序关闭...进行关键信息保存")
    }

    @AParameter
    @Executor(action = "重载指令")
    fun reloadConfig(bot: Bot, event: AnyMessageEvent) {
        val context = ContextProvider.initialize(event, bot)

        CommandList.reloadCommands()
        context.sendMsg("指令列表已重载")
        logInfo("指令列表已重载")
        val imageData = WebImgUtil.ImgData(
            imgName = "help-$lastHelpMd5",
            element = "body",
            url = "http://localhost:${webImgUtil.usePort}/help"
        )
        if (lastHelpMd5 != helpMd5) {
            webImgUtil.deleteImg(imageData)
            logInfo("帮助缓存已删除")
        }
        lastHelpMd5 = helpMd5
    }


    @Order(0)
    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "(.*)")
    fun totalDistribution(bot: Bot, event: AnyMessageEvent, matcher: Matcher) {
        val context = ContextProvider.initialize(event, bot)

        val todayUpMessage = dailyActiveJson["data"].last() as ObjectNode
        todayUpMessage.put("totalUpMessages", todayUpMessage["totalUpMessages"].intValue() + 1) // 当前消息数量加一
        val realId = OtherUtil().getRealId(event)

        // 将 usersNode 转换为一个可变列表
        val usersNode = dailyActiveJson["users"] as ArrayNode
        val usersText = usersNode.toString()
        val users: MutableSet<String> = if (usersText.isNotEmpty()) mapper.readValue(usersText) else mutableSetOf()

        // 添加 realId 到 users 列表中
        users.add(realId)
        val dailyActiveUsers = users.size
        todayUpMessage.put("dailyActiveUsers", dailyActiveUsers)
        // 将更新后的 users 列表转换为 ArrayNode
        val updatedUsersNode = mapper.valueToTree<ArrayNode>(users)
        // 更新 dailyActiveJson 中的 users 节点
        (dailyActiveJson as ObjectNode).replace("users", updatedUsersNode)

        val match = matcher.group(1)
        val matchedCommand = CommandList.commandList.firstOrNull { it.toRegex().matches(match) }
        if (matchedCommand == null) {
            val matchedCommands = OtherUtil().findMatchingStrings(match, CommandList.commands)
            val descriptions = getDescriptionsForCommands(matchedCommands, CommandList.helpList)

            // 检查帮助配置文件中的 checkCmd 配置，true 则开启未知指令拦截并发送帮助信息
            val template = templateService.searchByBotIdAndTemplateName(bot.selfId, "help")

            if (!CommandList.CHECK_COMMAND && template == null) return
            val templateJson = template!!.content?.let { JacksonUtil.readTree(it) } as ObjectNode
            val paramsArray = templateJson.with("markdown").withArray("params") as ArrayNode

            paramsArray.forEach { param ->
                param as ObjectNode
                val key = param.get("key").asText()
                updateParam(param, key, matchedCommands, descriptions)
            }

            //            val encodedInputBase64 =
            //                Base64.getEncoder().encodeToString(templateJson.toString().toByteArray())
            //            context.sendMsg(event, "[CQ:markdown,data=base64://$encodedInputBase64]", false)

            // TODO 临时补丁，被动Md被修复 先直发一下进行兜底回复 未来增加md开关
            context.sendMsg("未知指令，你可能在找这些指令：$matchedCommands")
            return
        }

        // 扫描包，这里直接扫描Demo所在的包
        actionFactory.newInstance().scanAction(TencentBotKotlinApplication::class.java)
        addition.doRequest(match, bot, event)
    }
}
