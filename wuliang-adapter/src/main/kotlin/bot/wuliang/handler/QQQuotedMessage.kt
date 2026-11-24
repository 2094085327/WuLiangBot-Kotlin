package bot.wuliang.handler

import bot.wuliang.adapter.context.QuotedMessage
import bot.wuliang.message.BotMessage

private const val MAX_QUOTE_PARTS = 64
private const val MAX_QUOTE_DEPTH = 4

internal fun parseQQQuote(metadata: Map<*, *>): QuotedMessage? {
    val scene = metadata["message_scene"] as? Map<*, *>
    val extensions = scene?.get("ext") as? List<*>
    val reference = extensions.orEmpty().asSequence().filterIsInstance<String>()
        .firstOrNull { it.startsWith("ref_msg_idx=") }?.substringAfter('=')?.takeIf { it.isNotBlank() }
    if (metadata.messageType() != 103 && reference == null) return null

    val elements = (metadata["msg_elements"] as? List<*>).orEmpty()
    val target = when {
        elements.size > MAX_QUOTE_PARTS -> null
        elements.size == 1 -> (elements.first() as? Map<*, *>)?.takeIf {
            val index = it["msg_idx"] as? String
            reference == null || index.isNullOrBlank() || index == reference
        }

        reference != null -> elements.filterIsInstance<Map<*, *>>().singleOrNull { it["msg_idx"] == reference }
        else -> null
    }
    val messages = mutableListOf<BotMessage>()
    var remaining = MAX_QUOTE_PARTS
    fun read(element: Map<*, *>, depth: Int): Boolean {
        if (--remaining < 0 || depth > MAX_QUOTE_DEPTH) return false
        val type = element.messageType()
        if (type == 102 || (depth > 0 && type == 103)) return true
        (element["content"] as? String)?.takeIf { it.isNotBlank() }?.let { messages.add(BotMessage.Text(it)) }

        val attachments = (element["attachments"] as? List<*>).orEmpty()
        remaining -= attachments.size
        if (remaining < 0) return false
        for (entry in attachments) {
            val attachment = entry as? Map<*, *> ?: continue
            val url = attachment["url"] as? String ?: continue
            if (!url.startsWith("https://", ignoreCase = true)) continue
            val name = attachment["filename"] as? String ?: ""
            val mime = attachment["content_type"] as? String ?: continue
            messages += if (mime.startsWith("image/", ignoreCase = true)) {
                BotMessage.Image(url, byteArrayOf(), mime, name)
            } else {
                BotMessage.File(url, name)
            }
        }
        val children = (element["msg_elements"] as? List<*>).orEmpty()
        if (children.size > remaining) return false
        return children.all { it is Map<*, *> && read(it, depth + 1) }
    }

    val complete = target != null && read(target, 0)
    return QuotedMessage(if (complete) messages else emptyList())
}

private fun Map<*, *>.messageType(): Int? = (this["message_type"] as? Number)?.toInt()