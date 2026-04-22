package bot.wuliang.handler

import bot.wuliang.adapter.message.MessageSender
import bot.wuliang.adapter.context.ExecutionContext
import bot.wuliang.adapter.context.RequestContext
import bot.wuliang.adapter.QQMessageSender
import bot.wuliang.botLog.logUtil.LoggerUtils.logError
import bot.wuliang.botLog.logUtil.LoggerUtils.logInfo
import bot.wuliang.message.BotMessage
import bot.wuliang.message.MessageBus
import io.github.kloping.qqbot.Starter
import io.github.kloping.qqbot.api.v2.FriendMessageEvent
import io.github.kloping.qqbot.api.v2.GroupMessageEvent
import io.github.kloping.qqbot.api.v2.MessageV2Event
import io.github.kloping.qqbot.entities.ex.Image
import io.github.kloping.qqbot.entities.ex.PlainText
import io.github.kloping.qqbot.impl.ListenerHost
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.springframework.stereotype.Component
import java.util.UUID
import javax.annotation.PostConstruct
import javax.annotation.PreDestroy
import kotlin.coroutines.cancellation.CancellationException

/**
 * QQ消息处理器
 *
 * 负责监听和处理来自QQ平台的消息事件。将QQ原生消息事件转换为统一的 [ExecutionContext] 格式，
 * 并通过 [MessageBus] 进行消息分发处理。支持群消息和好友消息两种类型
 *
 * @property messageBus 消息总线，用于分发消息到命令处理器
 * @property starter QQ机器人启动器，用于注册监听器和启动服务
 */
@Component
class QQMessageHandler(private val messageBus: MessageBus, private val starter: Starter) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    /**
     * 初始化QQ消息监听器
     *
     * 在Spring Bean创建后自动调用，注册消息监听器并启动QQ机器人服务
     */
    @PostConstruct
    fun init() {
        registerMessageListener()
        logInfo("QQ消息监听器启动完成")
        starter.run()
    }

    /**
     * 清理资源
     *
     * 在Spring Bean销毁前自动调用，取消所有协程任务并释放资源
     */
    @PreDestroy
    fun cleanup() {
        scope.cancel("应用程序关闭")
        logInfo("QQ消息监听器资源清理完成")
    }

    /**
     * 注册QQ消息监听器
     *
     * 向QQ机器人启动器注册事件监听器，监听所有类型的消息事件（包括群消息和好友消息）
     * 接收到消息后在协程中异步处理，异常会被捕获并记录日志
     */
    private fun registerMessageListener() {
        starter.registerListenerHost(object : ListenerHost() {
            @EventReceiver
            fun onMessage(event: MessageV2Event) {

                scope.launch {
                    try {
                        handleQQMessage(event)
                    } catch (e: CancellationException) {
                        logInfo("消息处理被取消: ${e.message}")
                    } catch (e: Exception) {
                        logError("未能处理用户发出的QQ消息: ${event.message}", e)
                    }
                }
            }
        })
    }

    /**
     * 处理QQ消息事件
     *
     * 将QQ原生的 [MessageV2Event] 转换为统一的执行上下文：
     * - 提取消息内容（文本和图片）
     * - 构建请求上下文（平台、用户ID、群组ID、消息类型等）
     * - 创建执行上下文并分发给消息总线
     *
     * @param event QQ消息事件
     */
    private suspend fun handleQQMessage(event: MessageV2Event) {
        val sender = QQMessageSender(event)
        val botMessage = event.toBotMessage()
        val chain = event.message
        val sb = StringBuilder()
        for (msg in chain) {
            if (msg is PlainText) {
                sb.append(msg.text)
            }
        }
        val requestContext = RequestContext(
            platform = "QQ",
            userId = event.sender.id,
            botId = event.bot.id,
            groupId = when (event) {
                is GroupMessageEvent -> event.subject.id
                else -> null
            },
            messageType = when (event) {
                is GroupMessageEvent -> "group"
                is FriendMessageEvent -> "private"
                else -> "unknown"
            },
            rawMessage = sb.toString().trim()
        )
        
        val executionContext = object : ExecutionContext {
            override val sender: MessageSender = sender
            override val requestContext: RequestContext = requestContext
            override val messages: List<BotMessage> = botMessage
        }
        
        messageBus.dispatch(executionContext)
    }

    /**
     * 将QQ消息事件转换为统一的Bot消息格式
     *
     * 遍历QQ消息链，将不同类型的消息元素转换为对应的 [BotMessage] 对象：
     * - [Image] -> [BotMessage.Image]
     * - [PlainText] -> [BotMessage.Text]
     *
     * @return Bot消息列表
     */
    private fun MessageV2Event.toBotMessage(): List<BotMessage> {
        return message.mapNotNull {
            when (it) {
                is Image -> BotMessage.Image(
                    url = it.url ?: "",
                    bytes = it.bytes ?: byteArrayOf(),
                    type = "image/jpeg",
                    fileName = "${UUID.randomUUID()}.jpg"
                )

                is PlainText -> BotMessage.Text(it.text)
                else -> null
            }
        }
    }
}
