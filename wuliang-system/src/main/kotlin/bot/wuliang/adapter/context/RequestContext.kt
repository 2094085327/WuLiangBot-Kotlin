package bot.wuliang.adapter.context

/**
 * 请求上下文
 */
data class RequestContext(
    val platform: String,    // 平台标识
    val botId: String,    // 机器人ID
    val userId: String,    // 用户ID
    val groupId: String?,    // 群组ID（可选）
    val messageType: String,    // 消息类型
    val rawMessage: String?      // 原始消息内容
)
