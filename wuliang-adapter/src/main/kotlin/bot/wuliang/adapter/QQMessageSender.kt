package bot.wuliang.adapter

import bot.wuliang.adapter.message.ImageSource
import bot.wuliang.adapter.message.MessageSegment
import bot.wuliang.adapter.message.MessageSender
import bot.wuliang.adapter.message.UniversalMessage
import io.github.kloping.qqbot.api.SendAble
import io.github.kloping.qqbot.api.message.MessageChannelReceiveEvent
import io.github.kloping.qqbot.api.v2.FriendMessageEvent
import io.github.kloping.qqbot.api.v2.GroupMessageEvent
import io.github.kloping.qqbot.api.v2.MessageV2Event
import io.github.kloping.qqbot.entities.ex.Image
import io.github.kloping.qqbot.entities.ex.Markdown
import io.github.kloping.qqbot.entities.ex.MessageAsyncBuilder
import io.github.kloping.qqbot.entities.ex.PlainText

/**
 * QQ消息发送器
 */
class QQMessageSender(private val event: MessageV2Event) : MessageSender {

    /**
     * 消息发送方法
     *
     * @param msg 消息结构
     */
    override suspend fun send(msg: UniversalMessage) {
        val builder = MessageAsyncBuilder()

        msg.segments.map { it.toQQElement() }.forEach { builder.append(it) }

        when (val qqEvent = event) {
            is GroupMessageEvent -> qqEvent.sendMessage(builder.build())
            is FriendMessageEvent -> qqEvent.send(builder.build())
            is MessageChannelReceiveEvent -> qqEvent.send(builder.build())
        }
    }

    /**
     * 将消息段转换为QQ消息元素
     */
    private fun MessageSegment.toQQElement(): SendAble = when (this) {
        is MessageSegment.Text -> PlainText(content)

        is MessageSegment.Image -> {
            when (val source = this.source) {
                is ImageSource.Url -> Image(source.url)
                is ImageSource.Bytes -> Image(source.bytes)
                is ImageSource.File -> throw UnsupportedOperationException("当前QQ适配器不支持直接发送文件图片,请使用URL或者Bytes发送图片")
            }
        }

        is MessageSegment.Markdown ->{
            val markdown = Markdown(null)
            markdown.content = content
            markdown
        }
    }
}