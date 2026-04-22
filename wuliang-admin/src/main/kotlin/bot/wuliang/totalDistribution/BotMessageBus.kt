package bot.wuliang.totalDistribution

import bot.wuliang.adapter.command.CommandRegistry
import bot.wuliang.adapter.context.ExecutionContext
import bot.wuliang.botLog.database.entity.LogEntity
import bot.wuliang.botLog.database.service.LogService
import bot.wuliang.config.DirectivesConfig.DIRECTIVES_KEY
import bot.wuliang.entity.DirectivesEntity
import bot.wuliang.message.BotMessage
import bot.wuliang.message.MessageBus
import bot.wuliang.otherUtil.OtherUtil
import bot.wuliang.redis.RedisService
import bot.wuliang.service.BotConfigService
import bot.wuliang.service.DirectivesService
import bot.wuliang.text.Convert
import org.springframework.stereotype.Service
import java.time.LocalDateTime

/**
 * 机器人消息总线服务类
 *
 * 负责处理机器人接收到的消息，进行指令匹配、分发和执行。
 * 实现了MessageBus接口，作为消息处理的核心入口
 *
 * @property otherUtil 其他工具类实例
 * @property directivesService 指令服务实例
 * @property botConfigService 机器人配置服务实例
 * @property redisService Redis服务实例
 * @property logService 日志服务实例
 */
@Service
class BotMessageBus(
    private val otherUtil: OtherUtil,
    private val directivesService: DirectivesService,
    private val botConfigService: BotConfigService,
    private val redisService: RedisService,
    private val logService: LogService
) : MessageBus {

    /**
     * 分发消息到对应的处理器
     *
     * 主要流程：
     * 1. 检查指令总开关是否启用
     * 2. 提取并匹配用户输入的指令
     * 3. 执行匹配到的命令或返回未知指令提示
     *
     * @param context 执行上下文，包含请求信息和发送器
     */
    override suspend fun dispatch(context: ExecutionContext) {
        val startTime = System.currentTimeMillis()

        // 指令总开关
        val allEnableConfig = Convert.toBool(botConfigService.selectConfigByKey("bot.directives.allEnable"))
        if (!allEnableConfig) {
            context.sender.sendText("无量姬当前所有的指令都被关闭了，可能正在维护中~")
            return
        }

        val match = extractMatch(context.messages) ?: return

        val directivesList = directivesService.selectDirectivesMatch(match)
        val directivesMatch = directivesList.any { directive ->
            directive.regex?.toRegex()?.matches(match) ?: false
        }

        // 未匹配处理
        if (!directivesMatch) {
            handleUnknownCommand(context, match, startTime)
            return
        }

        // 命令执行
        val commandWithMatcher = CommandRegistry.getCommandWithMatcher(match)
        if (commandWithMatcher != null) {
            val (command, cmdMatcher, _, _) = commandWithMatcher
            try {
                val result = command.execute(context, cmdMatcher)
                if (result is String && result.isNotEmpty()) {
                    context.sender.sendText(result)
                }
                return
            } catch (e: Exception) {
                context.sender.sendText("执行命令时发生错误: ${e.message}")
                e.printStackTrace()
                return
            }
        } else {
            context.sender.sendText("找不到命令")
            return
        }
    }

    /**
     * 处理未知指令
     *
     * 当用户输入的指令无法匹配时，提供相似指令建议并记录日志
     *
     * @param context 执行上下文
     * @param match 用户输入的指令文本
     * @param startTime 消息处理开始时间戳，用于计算耗时
     */
    private suspend fun handleUnknownCommand(context: ExecutionContext, match: String, startTime: Long) {
        val directivesList = redisService.getValueTyped<List<DirectivesEntity>>(DIRECTIVES_KEY)

        val matchedCommands = directivesList?.let { directive ->
            otherUtil.findMatchingStrings(
                match,
                directive.filter { it.enable == 1 }.map { it.directiveName!! }
            )
        }

        context.sender.sendText("未知指令，你可能在找这些指令：$matchedCommands")

        val endTime = System.currentTimeMillis()
        val logEntity = LogEntity(
            businessName = "未知指令",
            classPath = "BotMessageBus",
            methodName = "handleUnknownCommand",
            cmdText = match,
            eventType = context.requestContext.let { "${it.platform}_${it.messageType}" },
            groupId = context.requestContext.groupId,
            userId = context.requestContext.userId,
            botId = context.requestContext.botId,
            costTime = endTime - startTime,
            createTime = LocalDateTime.now()
        )
        logService.insertLog(logEntity)
    }

    /**
     * 从消息列表中提取指令文本
     *
     * 遍历消息列表，提取第一个文本消息的内容，并去除前后空格和前缀"/"。
     *
     * @param message 机器人消息列表
     * @return 提取后的指令文本，如果没有文本消息则返回null
     */
    private fun extractMatch(message: List<BotMessage>): String? {
        return message.firstNotNullOfOrNull { botMessage ->
            if (botMessage is BotMessage.Text) {
                botMessage.content.trim().removePrefix("/")
            } else null
        }
    }
}

