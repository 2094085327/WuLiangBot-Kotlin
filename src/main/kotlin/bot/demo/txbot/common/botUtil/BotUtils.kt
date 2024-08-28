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
    object ContextProvider {
//        var currentEvent: MessageEvent? = null
//        private var currentBot: Bot? = null

        /*        fun <T : MessageEvent> initialize(event: T, bot: Bot) {
                    currentEvent = event
                    currentBot = bot
                }*/

        fun <T : MessageEvent> initialize(event: T, bot: Bot): Context {
            println(event)
            println(bot)
            return Context(event, bot)
        }

        class Context(private val event: MessageEvent, private val bot: Bot) {
            fun sendMsg(message: String, autoEscape: Boolean = false): ActionData<MsgId>? {
                println(event)

                return when (event) {
                    is PrivateMessageEvent -> bot.sendPrivateMsg(event.userId, message, autoEscape)
                    is GroupMessageEvent -> {
                        if (event.groupId != null) bot.sendGroupMsg(event.groupId, message, autoEscape)
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
        }

        /*        private fun getBotAndEvent(): Pair<Bot, MessageEvent> {
                    val event = currentEvent ?: throw IllegalStateException("Event 未设置")
                    val bot = currentBot ?: throw IllegalStateException("Bot 未设置")
                    return Pair(bot, event)
                }

                fun sendMsg(message: String, autoEscape: Boolean = false): ActionData<MsgId>? {
                    val (bot, event) = getBotAndEvent()
                    return when (event.messageType) {
                        "private" -> bot.sendPrivateMsg(event.userId, message, autoEscape)
                        "group" -> {
                            event as GroupMessageEvent
                            bot.sendGroupMsg(event.groupId, message, autoEscape)
                        }

                        else -> null
                    }

                }

                fun sendPrivateMsg(message: String, autoEscape: Boolean = false): ActionData<MsgId> {
                    val (bot, event) = getBotAndEvent()
                    return bot.sendPrivateMsg(event.userId, message, autoEscape)
                }

                fun sendGroupMsg(message: String, autoEscape: Boolean = false): ActionData<MsgId> {
                    val (bot, event) = getBotAndEvent()
                    return bot.sendGroupMsg((event as GroupMessageEvent).groupId, message, autoEscape)
                }

                fun deleteMsg(messageId: Int): ActionRaw? {
                    val (bot, event) = getBotAndEvent()
                    return when (event) {
                        is GroupMessageEvent -> bot.deleteMsg(event.groupId, bot.selfId, messageId)
                        is PrivateMessageEvent -> bot.deleteMsg(messageId)
                        else -> bot.deleteMsg(messageId)
                    }
                }*/
    }
}