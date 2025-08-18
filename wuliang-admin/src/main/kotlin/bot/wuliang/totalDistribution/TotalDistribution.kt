package bot.wuliang.totalDistribution

import bot.wuliang.botLog.database.service.impl.LogServiceImpl
import bot.wuliang.botLog.logAop.LogEntity
import bot.wuliang.botLog.logUtil.LoggerUtils.logInfo
import bot.wuliang.botUtil.BotUtils
import bot.wuliang.botUtil.BotUtils.ContextUtil.createContextVo
import bot.wuliang.command.CommandRegistry
import bot.wuliang.config.CommonConfig.DAILY_ACTIVE_PATH
import bot.wuliang.config.CommonConfig.RESTART_CONFIG
import bot.wuliang.config.DirectivesConfig.DIRECTIVES_KEY
import bot.wuliang.dailyAcitve.DailyActive
import bot.wuliang.distribute.actionConfig.ActionFactory
import bot.wuliang.distribute.actionConfig.Addition
import bot.wuliang.distribute.annotation.ActionService
import bot.wuliang.entity.DirectivesEntity
import bot.wuliang.otherUtil.OtherUtil
import bot.wuliang.redis.RedisService
import bot.wuliang.restart.Restart
import bot.wuliang.service.BotConfigService
import bot.wuliang.service.DirectivesService
import bot.wuliang.text.Convert
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
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
import org.springframework.context.ApplicationContext
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
class TotalDistribution @Autowired constructor(
    private val actionFactory: ActionFactory,
    private val addition: Addition,
    private val redisService: RedisService,
    private val logService: LogServiceImpl,
    private val dailyActive: DailyActive,
    private val directivesService: DirectivesService,
    private val botConfigService: BotConfigService,
    @Qualifier("otherUtil") private val otherUtil: OtherUtil
) {

    @Autowired
    private lateinit var applicationContext: ApplicationContext


    private val scope = CoroutineScope(Dispatchers.Default)
    private val mapper = ObjectMapper() // 获取 ObjectMapper 对象

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


    @PostConstruct
    fun initCommandRegistry() {
        // 初始化命令注册中心
        CommandRegistry.init(applicationContext)
        // 扫描命令
        CommandRegistry.scanCommands("bot.wuliang")
    }


    @Order(0)
    @AnyMessageHandler
    @Suppress("UNCHECKED_CAST")
    @MessageHandlerFilter(cmd = "(.*)")
    suspend fun totalDistribution(bot: Bot, event: AnyMessageEvent, matcher: Matcher) {
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

        val allEnableConfig =
            Convert.toBool(botConfigService.selectConfigByKey("bot.directives.allEnable"))
        if (!allEnableConfig) {
            context.sendMsg("无量姬当前所有的指令都被关闭了，可能正在维护中~")
            return
        }


        val directivesMatch = directivesService.selectDirectivesMatch(match).any { directive ->
            directive.regex?.toRegex()?.matches(match) ?: false
        }

        if (!directivesMatch) {
            val directivesList = redisService.getValue(DIRECTIVES_KEY) as List<DirectivesEntity>
            val matchedCommands = otherUtil.findMatchingStrings(match, directivesList.map { it.directiveName!! })
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


        // 使用命令模式替代的反射调用，支持正则表达式
        val commandWithMatcher = CommandRegistry.getCommandWithMatcher(match)
        if (commandWithMatcher != null) {
            try {
                val (command, cmdMatcher, isRegex) = commandWithMatcher
                // 如果命令是ReflectiveBotCommand并且是正则匹配，则调用带matcher的execute方法
                val result = if (command is CommandRegistry.ReflectiveBotCommand && isRegex) {
                    command.execute(context, cmdMatcher)
                } else {
                    command.execute(context)
                }
                // 如果命令返回了结果，发送给用户
                if (result.isNotEmpty()) {
                    context.sendMsg(result)
                }
            } catch (e: Exception) {
                context.sendMsg("执行命令时发生错误: ${e.message}")
                e.printStackTrace()
            }
        } else {
            context.sendMsg("找不到命令")
        }
    }
}
