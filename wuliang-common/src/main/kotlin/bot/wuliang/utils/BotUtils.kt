package bot.wuliang.utils

import io.github.kloping.qqbot.api.message.MessageChannelReceiveEvent
import io.github.kloping.qqbot.api.v2.FriendMessageEvent
import io.github.kloping.qqbot.api.v2.GroupMessageEvent
import io.github.kloping.qqbot.api.v2.MessageV2Event
import io.github.kloping.qqbot.entities.ex.MessageAsyncBuilder
import org.aspectj.lang.annotation.Aspect
import org.springframework.stereotype.Component


/**
 * @description: 消息发送封装
 * @author Nature Zero
 * @date 2024/8/16 下午2:32
 */
@Aspect
@Component
class BotUtils {
    object ContextUtil {
        // 创建Context的方法
        fun initializeContext(event: MessageV2Event): Context {
            return Context(event)
        }
    }

    class Context(private val event: MessageV2Event) {
        fun sendMsg(message: String): Any? {
            val builder = MessageAsyncBuilder()
            builder.append(message)
            return sendMsg(builder)
        }

        fun sendMsg(messageBuilder: MessageAsyncBuilder): Any? {
            when (event) {
                is GroupMessageEvent -> {
                    return event.sendMessage(messageBuilder.build())
                }

                is FriendMessageEvent -> {
                    return event.send(messageBuilder.build())
                }

                is MessageChannelReceiveEvent -> {
                    return event.send(messageBuilder.build())
                }

                else -> {
                    throw IllegalStateException("不支持的消息类型: ${event.javaClass.simpleName}")
                }
            }
        }

        fun getEvent(): MessageV2Event {
            return event
        }

        val message: String
            get() = event.message[0].toString().trimStart().trimEnd()

        // 获取用户ID的方法
        val userId: String
            get() = event.sender.id


        // 获取机器人ID的方法
        val botId: String
            get() = event.bot.id

        // 获取群组ID的方法（如果是群消息）
        val groupId: String?
            get() = when (event) {
                is GroupMessageEvent -> event.subject.id
                else -> null
            }


        // 获取消息类型
        val messageType: String
            get() = when (event) {
                is GroupMessageEvent -> "group"
                is FriendMessageEvent -> "private"
                else -> "unknown"
            }
    }
}