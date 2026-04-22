package bot.wuliang.message

/**
 * 机器人消息
 */
sealed class BotMessage {
    /**
     * 文本消息
     * @param content 消息内容
     */
    data class Text(val content: String) : BotMessage()

    /**
     * 图片消息
     * @param url 图片链接
     * @param bytes 图片字节数组
     * @param type 图片类型
     * @param fileName 文件名
     */
    data class Image(val url: String, val bytes: ByteArray, val type: String, val fileName: String) : BotMessage()

    /**
     * 音频消息
     * @param url 音频链接
     * @param fileName 文件名
     */
    data class Audio(val url: String, val fileName: String? = null) : BotMessage()

    /**
     * 文件消息
     * @param url 文件链接
     * @param fileName 文件名
     */
    data class File(val url: String, val fileName: String) : BotMessage()
}