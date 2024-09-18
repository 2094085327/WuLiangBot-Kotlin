package bot.demo.txbot.common.botUtil

import com.mikuac.shiro.core.Bot
import com.mikuac.shiro.dto.action.common.ActionData
import com.mikuac.shiro.dto.action.common.ActionRaw
import com.mikuac.shiro.dto.action.common.MsgId
import com.mikuac.shiro.dto.event.message.GroupMessageEvent
import com.mikuac.shiro.dto.event.message.MessageEvent
import com.mikuac.shiro.dto.event.message.PrivateMessageEvent
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
    fun <T : MessageEvent> initialize(event: T, bot: Bot): Context {
        return Context(event, bot)
    }

    class Context(private val event: MessageEvent, private val bot: Bot) {
        fun sendMsg(message: String, autoEscape: Boolean = false): ActionData<MsgId>? {
            return when (event) {
                is PrivateMessageEvent -> bot.sendPrivateMsg(event.userId, message, autoEscape)
                is GroupMessageEvent -> {
                    if (event.groupId != null) bot.sendGroupMsg(event.groupId, event.userId, message, autoEscape)
                    else bot.sendPrivateMsg(event.userId, message, autoEscape)
                }

                else -> throw IllegalStateException("不支持的消息类型")
            }
        }

        fun deleteMsg(messageId: Int): ActionRaw? {
            return when (event) {
                is GroupMessageEvent -> {
                    if (event.groupId != null) bot.deleteMsg(event.groupId, bot.selfId, messageId)
                    bot.deleteMsg(event.userId, bot.selfId, messageId)
                }

                is PrivateMessageEvent -> bot.deleteMsg(messageId)
                else -> bot.deleteMsg(messageId)
            }
        }

        fun getEvent(): MessageEvent {
            return event
        }

        fun getBot(): Bot {
            return bot
        }
    }
}