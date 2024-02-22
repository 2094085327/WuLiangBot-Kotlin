package bot.demo.txbot

import bot.demo.txbot.common.utils.WebImgUtil
import com.mikuac.shiro.annotation.AnyMessageHandler
import com.mikuac.shiro.annotation.GroupMessageHandler
import com.mikuac.shiro.annotation.MessageHandlerFilter
import com.mikuac.shiro.annotation.PrivateMessageHandler
import com.mikuac.shiro.annotation.common.Shiro
import com.mikuac.shiro.common.utils.MsgUtils
import com.mikuac.shiro.common.utils.ShiroUtils
import com.mikuac.shiro.core.Bot
import com.mikuac.shiro.core.BotPlugin.MESSAGE_BLOCK
import com.mikuac.shiro.dto.event.message.AnyMessageEvent
import com.mikuac.shiro.dto.event.message.GroupMessageEvent
import com.mikuac.shiro.dto.event.message.PrivateMessageEvent
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.io.File
import java.net.MalformedURLException
import java.net.URL
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.nio.file.Path
import java.nio.file.Paths
import java.util.*
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

    fun String.escapeUnicode(): String {
        val escapeBuilder = StringBuilder()
        for (char in this) {
            if (char.toInt() < 128) {
                escapeBuilder.append(char)
            } else {
                escapeBuilder.append("\\u").append(String.format("%04x", char.toInt()))
            }
        }
        return escapeBuilder.toString()
    }

    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "md(.*)")
    fun fun5(bot: Bot, event: AnyMessageEvent?, matcher: Matcher) {
        // JSON字符串
        val jsonString = "{\"markdown\":{\"custom_template_id\":\"102076980_1707299391\",\"params\":[{\"key\":\"text_start\",\"values\":[\"\\u6807\\u9898\"]},{\"key\":\"img_dec\",\"values\":[\"test\"]},{\"key\":\"img_url\",\"values\":[\"111\"]}]}}"

        println(jsonString)


        val utf_16= jsonString.toByteArray(StandardCharsets.UTF_8)
        // 使用 Base64 编码
        val encodedInputBase64 = Base64.getEncoder().encodeToString(matcher.group(1).toString().toByteArray())

//        println("Encoded Input (Unicode): $encodedInputUnicode")
        println("Encoded Input (Base64): $encodedInputBase64")
        println("[CQ:markdown,data=base64://$encodedInputBase64]")

        val sendMsg: String = "[CQ:markdown,data=base64://eyJtYXJrZG93biI6eyJ0ZW1wbGF0ZV9pZCI6MSwicGFyYW1zIjpbXX0sIm1zZ19pZCI6IiIsImtleWJvYXJkIjp7ImNvbnRlbnQiOnsiYm90X2FwcGlkIjoxMDIwNzQwNTksInJvd3MiOlt7ImJ1dHRvbnMiOlt7ImlkIjoiXzAiLCJyZW5kZXJfZGF0YSI6eyJsYWJlbCI6Ilx1NjMwOVx1OTRhZTEiLCJ2aXNpdGVkX2xhYmVsIjoiXHU1ZGYyXHU2MzA5XHU0ZTBiIn0sImFjdGlvbiI6eyJ0eXBlIjoyLCJlbnRlciI6dHJ1ZSwicGVybWlzc2lvbiI6eyJ0eXBlIjoyfSwidW5zdXBwb3J0X3RpcHMiOiJcdThiZjdcdTUzNDdcdTdlYTdRUSIsImRhdGEiOiJcdTYzMDdcdTRlZTQxIn19LHsiaWQiOiJfMSIsInJlbmRlcl9kYXRhIjp7ImxhYmVsIjoiXHU2MzA5XHU5NGFlMiIsInZpc2l0ZWRfbGFiZWwiOiJcdTVkZjJcdTYzMDlcdTRlMGIifSwiYWN0aW9uIjp7InR5cGUiOjIsImVudGVyIjp0cnVlLCJwZXJtaXNzaW9uIjp7InR5cGUiOjJ9LCJ1bnN1cHBvcnRfdGlwcyI6Ilx1OGJmN1x1NTM0N1x1N2VhN1FRIiwiZGF0YSI6Ilx1NjMwN1x1NGVlNDIifX1dfV19fX0=]"



        bot.sendMsg(event, "md消息测试", false)
        bot.sendMsg(event, "[CQ:markdown,data=base64://$encodedInputBase64]", false)
        bot.sendMsg(event, "[CQ:at,qq=${event!!.userId}]", false)
        bot.sendMsg(event,sendMsg, false)
    }
}

