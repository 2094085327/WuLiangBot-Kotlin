package bot.demo.txbot.other

import bot.demo.txbot.TencentBotKotlinApplication
import bot.demo.txbot.common.botUtil.BotUtils
import bot.demo.txbot.common.botUtil.BotUtils.Context
import bot.demo.txbot.common.database.template.TemplateService
import bot.demo.txbot.common.utils.JacksonUtil
import bot.demo.txbot.common.utils.LoggerUtils.logInfo
import bot.demo.txbot.common.utils.OtherUtil
import bot.demo.txbot.common.utils.OtherUtil.STConversion.toMd5
import bot.demo.txbot.common.utils.WebImgUtil
import bot.demo.txbot.other.TotalDistribution.CommandList.commandConfig
import bot.demo.txbot.other.TotalDistribution.CommandList.dailyActiveJson
import bot.demo.txbot.other.TotalDistribution.CommandList.helpMd5
import bot.demo.txbot.other.TotalDistribution.CommandList.lastHelpMd5
import bot.demo.txbot.other.distribute.actionConfig.ActionFactory
import bot.demo.txbot.other.distribute.actionConfig.Addition
import bot.demo.txbot.other.distribute.annotation.AParameter
import bot.demo.txbot.other.distribute.annotation.ActionService
import bot.demo.txbot.other.distribute.annotation.Executor
import bot.demo.txbot.other.vo.HelpVo.CommandConfig
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
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
import org.springframework.beans.factory.annotation.Qualifier
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
    @Autowired private val addition: Addition,
    @Qualifier("otherUtil") private val otherUtil: OtherUtil
) {

    private val scope = CoroutineScope(Dispatchers.Default)
    private val mapper = ObjectMapper() // 获取 ObjectMapper 对象

    object CommandList {
        private val jacksonMapper = jacksonObjectMapper()

        var commandConfig: CommandConfig =
            jacksonMapper.readValue(File(HELP_JSON))
        var helpMd5: String? = null

        // 是否启用命令检查
        private var CHECK_COMMAND = commandConfig.checkCmd

        // 上次更新help.json的Md5
        var lastHelpMd5: String? = commandConfig.updateMd5

        var dailyActiveJson = JacksonUtil.getJsonNode(DAILY_ACTIVE_PATH)

        init {
            reloadCommands()
        }

        fun reloadCommands() {
            commandConfig = jacksonMapper.readValue(File(HELP_JSON))
            CHECK_COMMAND = commandConfig.checkCmd

            // 根据Md5判断是否更新help.json
            lastHelpMd5 = commandConfig.updateMd5
            helpMd5 = commandConfig.allCmd.toString().toMd5()
            if (helpMd5 != lastHelpMd5) {
                commandConfig.updateMd5 = helpMd5 as String
                jacksonMapper.writeValue(
                    File(HELP_JSON),
                    commandConfig
                )
            }
        }
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
        logInfo("程序关闭...进行关键信息保存")
    }

    @AParameter
    @Executor(action = "重载指令")
    fun reloadConfig(context: Context) {
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
        val context = BotUtils().initialize(event, bot)
        val todayUpMessage = dailyActiveJson["data"].last() as ObjectNode
        todayUpMessage.put("totalUpMessages", todayUpMessage["totalUpMessages"].intValue() + 1) // 当前消息数量加一
        val realId = OtherUtil().getRealId(context.getEvent())

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

        // 这部分代码为Md帮助信息发送，目前无法使用
        /*        // 检查帮助配置文件中的 checkCmd 配置，true 则开启未知指令拦截并发送帮助信息
                val template = templateService.searchByBotIdAndTemplateName(bot.selfId, "help")
                if (!CommandList.CHECK_COMMAND && template == null) return
                val templateJson = template!!.content?.let { JacksonUtil.readTree(it) } as ObjectNode
                val paramsArray = templateJson.with("markdown").withArray("params") as ArrayNode
                paramsArray.forEach { param ->
                    param as ObjectNode
                    val key = param.get("key").asText()
                    updateParam(param, key, matchedCommands, descriptions)
                    //            val encodedInputBase64 =
//                Base64.getEncoder().encodeToString(templateJson.toString().toByteArray())
//            context.sendMsg(event, "[CQ:markdown,data=base64://$encodedInputBase64]", false)
                }*/

        if (!commandConfig.enableAll) {
            context.sendMsg("无量姬当前所有的指令都被关闭了，可能正在维护中~")
            return
        }
        val foundMatch = commandConfig.allCmd.any { command ->
            command.value.commendList
                .filter { it.enable } // 只过滤出启用的命令
                .any { it.regex.toRegex().matches(match) }
        }

        if (!foundMatch) {
            val commandList: List<String> = commandConfig.allCmd
                .flatMap { it.value.commendList } // 扁平化 commendList 列表
                .map { it.command } // 提取 command 字段
            val matchedCommands = otherUtil.findMatchingStrings(match, commandList)
            context.sendMsg("未知指令，你可能在找这些指令：$matchedCommands")
            return
        }


        // 扫描包，这里直接扫描Demo所在的包
        actionFactory.newInstance().scanAction(TencentBotKotlinApplication::class.java)
        addition.doRequest(match, context)
    }
}
