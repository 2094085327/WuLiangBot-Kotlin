package bot.wuliang.distribute

import bot.wuliang.TencentBotKotlinApplication
import bot.wuliang.botLog.database.service.impl.LogServiceImpl
import bot.wuliang.botLog.logAop.LogEntity
import bot.wuliang.botLog.logUtil.LoggerUtils.logInfo
import bot.wuliang.botUtil.BotUtils
import bot.wuliang.botUtil.BotUtils.ContextUtil.createContextVo
import bot.wuliang.config.DAILY_ACTIVE_PATH
import bot.wuliang.config.HELP_JSON
import bot.wuliang.config.RESTART_CONFIG
import bot.wuliang.dailyAcitve.DailyActive
import bot.wuliang.distribute.TotalDistribution.CommandList.commandConfig
import bot.wuliang.distribute.TotalDistribution.CommandList.helpMd5
import bot.wuliang.distribute.TotalDistribution.CommandList.lastHelpMd5
import bot.wuliang.distribute.actionConfig.ActionFactory
import bot.wuliang.distribute.actionConfig.Addition
import bot.wuliang.distribute.annotation.AParameter
import bot.wuliang.distribute.annotation.ActionService
import bot.wuliang.distribute.annotation.Executor
import bot.wuliang.imageProcess.WebImgUtil
import bot.wuliang.otherUtil.OtherUtil
import bot.wuliang.otherUtil.OtherUtil.STConversion.toMd5
import bot.wuliang.restart.Restart
import bot.wuliang.template.service.TemplateService
import bot.wuliang.vo.HelpVo
import com.fasterxml.jackson.databind.ObjectMapper
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
    @Autowired
    private lateinit var logService: LogServiceImpl

    @Autowired
    private lateinit var dailyActive: DailyActive
    private val scope = CoroutineScope(Dispatchers.Default)
    private val mapper = ObjectMapper() // 获取 ObjectMapper 对象

    object CommandList {
        private val jacksonMapper = jacksonObjectMapper()

        var commandConfig: HelpVo.CommandConfig =
            jacksonMapper.readValue(File(HELP_JSON))
        var helpMd5: String? = null

        // 是否启用命令检查
        private var CHECK_COMMAND = commandConfig.checkCmd

        // 上次更新help.json的Md5
        var lastHelpMd5: String? = commandConfig.updateMd5

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
                    """{"data":[{"date":"$currentTime","dailyActiveUsers":0,"totalUpMessages":0}]}""".trimIndent()
                file.writeText(json)
                logInfo("日活日志文件缺失，已自动创建")
            }
            val restartFile = File(RESTART_CONFIG)
            if (!restartFile.exists()) {
                restartFile.createNewFile()
                restartFile.writeText("""{"jar_file":"${Restart().getNowJarName()}"}""".trimIndent())
                logInfo("重启配置文件缺失，已自动创建")
            } else {
                val restartJson = mapper.readTree(restartFile) as ObjectNode
                restartJson.put("jar_file", Restart().getNowJarName())
                mapper.writeValue(restartFile, restartJson)
            }
        }
    }

    @EventListener
    fun endEventListenerShutdown(event: ContextClosedEvent) {
        // 保存日活日志
        dailyActive.initDailyActive()
        logInfo("程序关闭...进行关键信息保存")
    }

    @AParameter
    @Executor(action = "重载指令")
    fun reloadConfig(context: BotUtils.Context) {
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
        val startTime: Long = System.currentTimeMillis()
        val context = BotUtils().initialize(event, bot)

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
            val endTime: Long = System.currentTimeMillis()
            val contextVo = context.createContextVo()
            val logEntity = LogEntity(
                businessName = "未知指令",
                classPath = "TotalDistribution",
                methodName = "totalDistribution",
                cmdText = match,
                eventType = contextVo.messageType,
                groupId = contextVo.groupId,
                userId = contextVo.userId,
                botId = contextVo.botId,
                costTime = endTime - startTime,
                createTime = LocalDateTime.now()
            )
            logService.insertLog(logEntity)
            return
        }


        // 扫描包，这里直接扫描Demo所在的包
        actionFactory.newInstance().scanAction(TencentBotKotlinApplication::class.java)
        addition.doRequest(match, context)
    }
}
