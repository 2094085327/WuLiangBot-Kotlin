//package bot.demo.txbot
//
//import bot.demo.txbot.common.botUtil.BotUtils.ContextProvider
//import com.mikuac.shiro.annotation.AnyMessageHandler
//import com.mikuac.shiro.annotation.GroupMessageHandler
//import com.mikuac.shiro.annotation.MessageHandlerFilter
//import com.mikuac.shiro.annotation.PrivateMessageHandler
//import com.mikuac.shiro.annotation.common.Shiro
//import com.mikuac.shiro.common.utils.MsgUtils
//import com.mikuac.shiro.core.Bot
//import com.mikuac.shiro.dto.event.message.AnyMessageEvent
//import com.mikuac.shiro.dto.event.message.GroupMessageEvent
//import com.mikuac.shiro.dto.event.message.PrivateMessageEvent
//import org.springframework.stereotype.Component
//import java.nio.charset.StandardCharsets
//import java.util.*
//import java.util.regex.Matcher
//
//
//@Shiro
//@Component
//class ExamplePlugin {
//    // 更多用法详见 @MessageHandlerFilter 注解源码
//    // 当机器人收到的私聊消息消息符合 cmd 值 "hi" 时，这个方法会被调用。
//
//    @PrivateMessageHandler
//    @MessageHandlerFilter(cmd = "hi")
//    fun fun1(bot: Bot, event: PrivateMessageEvent, matcher: Matcher?) {
//        ContextProvider.initialize(event, bot)
//
//        // 构建消息
//        val sendMsg: String = MsgUtils.builder().text("Hello, this is shiro demo.").build()
//        // 发送私聊消息
//        bot.sendPrivateMsg(event.userId, sendMsg, false)
//    }
//
//
//    @GroupMessageHandler
//    @MessageHandlerFilter(cmd = "123")
//    fun fun2(bot: Bot, event: GroupMessageEvent, matcher: Matcher?) {
//        ContextProvider.initialize(event, bot)
//
//        // 构建消息
//        val sendMsg: String = MsgUtils.builder().text("Hello, this is shiro demo.").build()
//        // 发送群组消息
//        bot.sendGroupMsg(event.groupId, sendMsg, false)
//    }
//
//
//    // 如果 at 参数设定为 AtEnum.NEED 则只有 at 了机器人的消息会被响应
//    @AnyMessageHandler
//    @MessageHandlerFilter(cmd = "(.*)hi")
//    fun fun3(bot: Bot, event: AnyMessageEvent, matcher: Matcher?) {
//        ContextProvider.initialize(event, bot)
//
//        // 以注解方式调用可以根据自己的需要来为方法设定参数
//        // 例如群组消息可以传递 GroupMessageEvent, Bot, Matcher 多余的参数会被设定为 null
//        println(event.message)
//        val sendMsg = "你好！这里是无量姬！你刚刚发送了：${matcher?.group(1)}"
//        ContextProvider.sendMsg(sendMsg)
//        val replayMsg = MsgUtils.builder().reply(event.messageId).text("收到！").build()
//        ContextProvider.sendMsg(replayMsg)
//    }
//
//
//    // 同时监听群组及私聊消息 并根据消息类型（私聊，群聊）回复
//    @AnyMessageHandler
//    @MessageHandlerFilter(cmd = "say hello")
//    fun fun4(bot: Bot, event: AnyMessageEvent) {
//        ContextProvider.initialize(event, bot)
//        ContextProvider.sendMsg("hello")
//    }
//
//    fun String.escapeUnicode(): String {
//        val escapeBuilder = StringBuilder()
//        for (char in this) {
//            if (char.toInt() < 128) {
//                escapeBuilder.append(char)
//            } else {
//                escapeBuilder.append("\\u").append(String.format("%04x", char.toInt()))
//            }
//        }
//        return escapeBuilder.toString()
//    }
//
//
//    @AnyMessageHandler
//    @MessageHandlerFilter(cmd = "md(.*)")
//    fun fun5(bot: Bot, event: AnyMessageEvent, matcher: Matcher) {
//        ContextProvider.initialize(event, bot)
//
//        // JSON字符串
//        val jsonString =
//            "{\"markdown\":{\"custom_template_id\":\"102076980_1707299391\",\"params\":[{\"key\":\"text_start\",\"values\":[\"你好\"]},{\"key\":\"img_dec\",\"values\":[\"这是图片\"]},{\"key\":\"img_url\",\"values\":[\"https://telegraph-image-dej.pages.dev/file/eb0592eb0220a995f2e3f.jpg\"]}]}}"
//
//        println(jsonString)
//
//
//        val utf_16 = jsonString.toByteArray(StandardCharsets.UTF_8)
//        // 使用 Base64 编码
//        val encodedInputBase64 = Base64.getEncoder().encodeToString(matcher.group(1).toString().toByteArray())
//        val encodedInputBase642 = Base64.getEncoder().encodeToString(jsonString.toString().toByteArray())
//
////        println("Encoded Input (Unicode): $encodedInputUnicode")
//        println("Encoded Input (Base64): $encodedInputBase64")
//        println("[CQ:markdown,data=base64://$encodedInputBase64]")
//
//        val sendMsg: String =
//            "[CQ:markdown,data=base64://eyJtYXJrZG93biI6eyJ0ZW1wbGF0ZV9pZCI6MSwicGFyYW1zIjpbXX0sIm1zZ19pZCI6IiIsImtleWJvYXJkIjp7ImNvbnRlbnQiOnsiYm90X2FwcGlkIjoxMDIwNzQwNTksInJvd3MiOlt7ImJ1dHRvbnMiOlt7ImlkIjoiXzAiLCJyZW5kZXJfZGF0YSI6eyJsYWJlbCI6Ilx1NjMwOVx1OTRhZTEiLCJ2aXNpdGVkX2xhYmVsIjoiXHU1ZGYyXHU2MzA5XHU0ZTBiIn0sImFjdGlvbiI6eyJ0eXBlIjoyLCJlbnRlciI6dHJ1ZSwicGVybWlzc2lvbiI6eyJ0eXBlIjoyfSwidW5zdXBwb3J0X3RpcHMiOiJcdThiZjdcdTUzNDdcdTdlYTdRUSIsImRhdGEiOiJcdTYzMDdcdTRlZTQxIn19LHsiaWQiOiJfMSIsInJlbmRlcl9kYXRhIjp7ImxhYmVsIjoiXHU2MzA5XHU5NGFlMiIsInZpc2l0ZWRfbGFiZWwiOiJcdTVkZjJcdTYzMDlcdTRlMGIifSwiYWN0aW9uIjp7InR5cGUiOjIsImVudGVyIjp0cnVlLCJwZXJtaXNzaW9uIjp7InR5cGUiOjJ9LCJ1bnN1cHBvcnRfdGlwcyI6Ilx1OGJmN1x1NTM0N1x1N2VhN1FRIiwiZGF0YSI6Ilx1NjMwN1x1NGVlNDIifX1dfV19fX0=]"
//
//
//        println("1231231231")
//
//        ContextProvider.sendMsg("md消息测试")
//        ContextProvider.sendMsg("[CQ:markdown,data=base64://$encodedInputBase642]")
//    }
//
//    // 撤回消息
//
//    @AnyMessageHandler
//    @MessageHandlerFilter(cmd = "3333")
//    fun fun7(bot: Bot, event: AnyMessageEvent) {
//        ContextProvider.initialize(event, bot)
//
//        val send = ContextProvider.sendMsg("hello")
//        bot.deleteMsg(event.groupId, bot.selfId, send.data.messageId)
//    }
//
//}
//
