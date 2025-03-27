package bot.wuliang.botUtil.vo

/**
 * 消息参数
 *
 */
data class ContextVo(
    val messageType: String, //group群聊 private私聊
    val groupId: String? = null,
    val userId: String,
    val botId: Long
)
