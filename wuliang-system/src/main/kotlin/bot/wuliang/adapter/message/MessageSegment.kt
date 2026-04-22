package bot.wuliang.adapter.message

/**
 * 消息段
 */
sealed class MessageSegment {
    /**
     * 纯文本
     * @param content 文本内容
     */
    data class Text(val content: String) : MessageSegment()

    /**
     * 图片 (支持 URL 或 本地文件路径)
     * @param source 图片来源
     */
    data class Image(val source: ImageSource) : MessageSegment()

    /**
     * Markdown
     * @param content Markdown 内容
     */
    data class Markdown(val content: String) : MessageSegment()
}