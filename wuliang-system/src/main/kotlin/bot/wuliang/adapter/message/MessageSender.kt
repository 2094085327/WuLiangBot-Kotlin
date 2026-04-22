package bot.wuliang.adapter.message


/**
 * 消息发送器接口
 */
interface MessageSender {
    /**
     * 发送构建的消息链
     */
    suspend fun send(msg: UniversalMessage)

    /**
     * 发送纯文本消息
     */
    suspend fun sendText(text: String) {
        send(UniversalMessage.Builder().text(text).build())
    }

    /**
     * 发送图片消息
     */
    suspend fun sendImage(url: String) {
        send(UniversalMessage.Builder().image(url).build())
    }

}