package bot.wuliang.totalDistribution

import bot.wuliang.botLog.database.service.impl.LogServiceImpl
import bot.wuliang.botLog.logAop.LogEntity
import bot.wuliang.botLog.logUtil.LoggerUtils.logInfo
import bot.wuliang.utils.BotUtils.ContextUtil.initializeContext
import bot.wuliang.command.CommandRegistry
import bot.wuliang.config.CommonConfig.DAILY_ACTIVE_PATH
import bot.wuliang.config.CommonConfig.RESTART_CONFIG
import bot.wuliang.config.DirectivesConfig.DIRECTIVES_KEY
import bot.wuliang.dailyAcitve.DailyActive
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
import io.github.kloping.qqbot.Starter
import io.github.kloping.qqbot.api.Intents
import io.github.kloping.qqbot.api.v2.MessageV2Event
import io.github.kloping.qqbot.impl.ListenerHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.ApplicationContext
import org.springframework.context.event.ContextClosedEvent
import org.springframework.context.event.EventListener
import org.springframework.stereotype.Component
import java.io.File
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.annotation.PostConstruct


/**
 * @description: 总分发方法，将指令分发至各类
 * @author Nature Zero
 * @date 2024/7/13 下午8:41
 */
@Component
@ActionService
class TotalDistribution @Autowired constructor(
    private val redisService: RedisService,
    private val logService: LogServiceImpl,
    private val dailyActive: DailyActive,
    private val directivesService: DirectivesService,
    private val botConfigService: BotConfigService,
    @Qualifier("otherUtil") private val otherUtil: OtherUtil,
    @Value("\${wuLiang.bot-config.appid}") private var appid: String,
    @Value("\${wuLiang.bot-config.token}") private var token: String,
    @Value("\${wuLiang.bot-config.secret}") private var secret: String
) {

    @Autowired
    private lateinit var applicationContext: ApplicationContext

    private lateinit var starter: Starter

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
    fun initJavaQQBotSdk() {
        starter = Starter(appid, token, secret)
        starter.config.code = Intents.PUBLIC_INTENTS.and(Intents.GROUP_INTENTS)
        starter.run()
        starter.registerListenerHost(object : ListenerHost() {
            // 新SDK消息监听器
            @EventReceiver
            fun onMessage(event: MessageV2Event) {
               runBlocking{
                   handleQQMessage(event)
               }
            }
        })
    }

    private suspend fun handleQQMessage(event: MessageV2Event) {
        val startTime: Long = System.currentTimeMillis()
        val context =   initializeContext(event)
        val match =  context.message

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
            val directivesList = redisService.getValueTyped<List<DirectivesEntity>>(DIRECTIVES_KEY)
            val matchedCommands = directivesList?.let {directive-> otherUtil.findMatchingStrings(match, directive.map { it.directiveName!! }) }
            context.sendMsg("未知指令，你可能在找这些指令：$matchedCommands")
            val endTime: Long = System.currentTimeMillis()
            // 简化处理新SDK的上下文信息
            val logEntity = LogEntity(
                businessName = "未知指令",
                classPath = "TotalDistribution",
                methodName = "handleNewSdkMessage",
                cmdText = match,
                eventType = context.messageType,
                groupId = context.groupId,
                userId = context.userId,
                botId = context.botId,
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


    @PostConstruct
    fun initCommandRegistry() {
        // 初始化命令注册中心
        CommandRegistry.init(applicationContext)
        // 扫描命令
        CommandRegistry.scanCommands("bot.wuliang")
    }
}
