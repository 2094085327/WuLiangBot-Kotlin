package bot.demo.txbot

import bot.demo.txbot.common.utils.WebImgUtil
import com.mikuac.shiro.annotation.AnyMessageHandler
import com.mikuac.shiro.annotation.GroupMessageHandler
import com.mikuac.shiro.annotation.MessageHandlerFilter
import com.mikuac.shiro.annotation.PrivateMessageHandler
import com.mikuac.shiro.annotation.common.Shiro
import com.mikuac.shiro.common.utils.MsgUtils
import com.mikuac.shiro.core.Bot
import com.mikuac.shiro.dto.event.message.AnyMessageEvent
import com.mikuac.shiro.dto.event.message.GroupMessageEvent
import com.mikuac.shiro.dto.event.message.PrivateMessageEvent
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.io.File
import java.net.MalformedURLException
import java.net.URL
import java.nio.file.Path
import java.nio.file.Paths
import java.util.regex.Matcher
import java.util.regex.Pattern


@Shiro
@Component
class ExamplePlugin {
    // 更多用法详见 @MessageHandlerFilter 注解源码
    // 当机器人收到的私聊消息消息符合 cmd 值 "hi" 时，这个方法会被调用。
    @PrivateMessageHandler
    @MessageHandlerFilter(cmd = "hi")
    fun fun1(bot: Bot, event: PrivateMessageEvent, matcher: Matcher?) {
        // 构建消息
        val sendMsg: String = MsgUtils.builder().text("Hello, this is shiro demo.").build()
        // 发送私聊消息
        bot.sendPrivateMsg(event.userId, sendMsg, false)
    }

    @GroupMessageHandler
    @MessageHandlerFilter(cmd = "123")
    fun fun2(bot: Bot, event: GroupMessageEvent, matcher: Matcher?) {
        // 构建消息
        val sendMsg: String = MsgUtils.builder().text("Hello, this is shiro demo.").build()
        // 发送群组消息
        bot.sendGroupMsg(event.groupId, sendMsg, false)
    }

    // 如果 at 参数设定为 AtEnum.NEED 则只有 at 了机器人的消息会被响应
    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "(.*)hi")
    fun fun3(bot: Bot, event: AnyMessageEvent?, matcher: Matcher?) {
        // 以注解方式调用可以根据自己的需要来为方法设定参数
        // 例如群组消息可以传递 GroupMessageEvent, Bot, Matcher 多余的参数会被设定为 null
        if (event != null) {
            println(event.message)
            val sendMsg: String = "你好！这里是无量姬！你刚刚发送了：${matcher?.group(1)}"
            bot.sendMsg(event, sendMsg, false)
            val replayMsg = MsgUtils.builder().reply(event.messageId).text("收到！").build()
            bot.sendMsg(event, replayMsg, false)
        }
    }

    // 同时监听群组及私聊消息 并根据消息类型（私聊，群聊）回复
    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "say hello")
    fun fun4(bot: Bot, event: AnyMessageEvent?) {
        bot.sendMsg(event, "hello", false)
    }
}

