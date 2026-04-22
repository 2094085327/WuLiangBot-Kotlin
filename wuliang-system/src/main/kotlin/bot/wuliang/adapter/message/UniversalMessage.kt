package bot.wuliang.adapter.message

/**
 * 通用消息类
 */
data class UniversalMessage(val segments: List<MessageSegment> = emptyList()) {
    /**
     * 消息构建器
     */
    class Builder {
        private val segments = mutableListOf<MessageSegment>()

        // 添加文本、图片等消息段
        fun text(content: String) = apply { segments.add(MessageSegment.Text(content)) }
        fun image(url: String) = apply { segments.add(MessageSegment.Image(ImageSource.Url(url))) }
        fun imageLocal(path: String) = apply { segments.add(MessageSegment.Image(ImageSource.File(path))) }
        fun imageBytes(bytes: ByteArray) = apply { segments.add(MessageSegment.Image(ImageSource.Bytes(bytes))) }

        fun markdown(content: String) = apply { segments.add(MessageSegment.Markdown(content)) }
        fun build() = UniversalMessage(segments.toList())
    }
}