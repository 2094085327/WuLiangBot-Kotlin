package bot.wuliang.utils

import io.github.kloping.qqbot.api.message.MessageChannelReceiveEvent
import io.github.kloping.qqbot.api.v2.FriendMessageEvent
import io.github.kloping.qqbot.api.v2.GroupMessageEvent
import io.github.kloping.qqbot.api.v2.MessageV2Event
import io.github.kloping.qqbot.entities.ex.Image
import io.github.kloping.qqbot.entities.ex.MessageAsyncBuilder
import io.github.kloping.qqbot.entities.ex.PlainText
import io.github.kloping.qqbot.entities.qqpd.data.Emoji
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

    data class MessageContent(
        val text: String,
        val images: List<Image>,
        val emojis: List<Emoji>,
    )

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

        private fun parseMessage(): MessageContent {
            val textBuilder = StringBuilder()
            val images = mutableListOf<Image>()
            val emojis = mutableListOf<Emoji>()


            for (msg in event.message) {
                when (msg) {
                    is PlainText -> textBuilder.append(msg.text)
                    is Image -> images.add(msg)
                    is Emoji -> emojis.add(msg)
                }
            }

            return MessageContent(
                text = textBuilder.toString().trim(),
                images = images,
                emojis = emojis,
            )
        }

        var messageContent: MessageContent = parseMessage()

        val message: String
            get() {
                val chain = event.message
                val sb = StringBuilder()
                for (msg in chain) {
                    if (msg is PlainText) {
                        sb.append(msg.text)
                    }
                }
                return sb.toString().trim()
            }

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