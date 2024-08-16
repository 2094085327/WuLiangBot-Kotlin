package bot.demo.txbot.common.botUtil

import com.mikuac.shiro.core.Bot
import com.mikuac.shiro.dto.action.common.ActionData
import com.mikuac.shiro.dto.action.common.ActionRaw
import com.mikuac.shiro.dto.action.common.MsgId
import com.mikuac.shiro.dto.event.message.AnyMessageEvent
import com.mikuac.shiro.dto.event.message.GroupMessageEvent
import com.mikuac.shiro.dto.event.message.MessageEvent
import com.mikuac.shiro.dto.event.message.PrivateMessageEvent
import org.aspectj.lang.annotation.Aspect
import org.springframework.stereotype.Component


/**
 * @description: TODO
 * @author Nature Zero
 * @date 2024/8/16 下午2:32
 */
@Aspect
@Component
class BotUtils {
    object ContextProvider {
        var currentEvent: MessageEvent? = null
        private var currentBot: Bot? = null

        fun initialize(event: MessageEvent, bot: Bot) {
            currentEvent = event
            currentBot = bot
        }

        fun initialize(event: AnyMessageEvent, bot: Bot) {
            currentEvent = event
            currentBot = bot
        }

        fun initialize(event: GroupMessageEvent, bot: Bot) {
            currentEvent = event
            currentBot = bot
        }

        fun initialize(event: PrivateMessageEvent, bot: Bot) {
            currentEvent = event
            currentBot = bot
        }

        fun sendMsg(message: String, autoEscape: Boolean = false): ActionData<MsgId> {
            val event = currentEvent ?: throw IllegalStateException("Event 未设置")
            val bot = currentBot ?: throw IllegalStateException("Bot 未设置")
            return bot.sendMsg(event as AnyMessageEvent, message, autoEscape)
        }

        fun sendPrivateMsg(message: String, autoEscape: Boolean = false): ActionData<MsgId> {
            val event = currentEvent ?: throw IllegalStateException("Event 未设置")
            val bot = currentBot ?: throw IllegalStateException("Bot 未设置")
            return bot.sendPrivateMsg(event.userId, message, autoEscape)
        }

        fun sendGroupMsg(message: String, autoEscape: Boolean = false): ActionData<MsgId> {
            val event = currentEvent ?: throw IllegalStateException("Event 未设置")
            val bot = currentBot ?: throw IllegalStateException("Bot 未设置")
            return bot.sendGroupMsg((event as GroupMessageEvent).groupId, message, autoEscape)
        }

        fun deleteMsg(messageId: Int): ActionRaw? {
            val event = currentEvent ?: throw IllegalStateException("Event 未设置")
            val bot = currentBot ?: throw IllegalStateException("Bot 未设置")
            return when (event) {
                is GroupMessageEvent -> bot.deleteMsg(event.groupId, bot.selfId, messageId)
                is PrivateMessageEvent -> bot.deleteMsg(messageId)
                else -> bot.deleteMsg(messageId)
            }
        }
    }
}