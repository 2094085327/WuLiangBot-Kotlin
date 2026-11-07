package bot.wuliang.market

import bot.wuliang.adapter.context.RequestContext
import bot.wuliang.redis.RedisService
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.util.*
import java.util.concurrent.TimeUnit

data class PendingMarketScreenshot(val value: String) {
    val expiresAtMillis: Long get() = value.substringBefore(':').toLong()
}

/** 部件截图等待按平台、机器人、用户及会话隔离。 */
@Component
class MarketScreenshotSessions(private val redis: RedisService) {
    fun begin(context: RequestContext): PendingMarketScreenshot {
        val pending = PendingMarketScreenshot("${System.currentTimeMillis() + 60_000}:${UUID.randomUUID()}")
        // 接图期限为一分钟；记录多保留一段时间，供超时任务原子核对本轮等待。
        redis.setValueWithExpiry(key(context), pending.value, 300, TimeUnit.SECONDS)
        return pending
    }

    fun pending(context: RequestContext): PendingMarketScreenshot? {
        val value = redis.getValueOrThrow(key(context)) as? String ?: return null
        if ((value.substringBefore(':').toLongOrNull() ?: 0) <= System.currentTimeMillis()) return null
        return PendingMarketScreenshot(value)
    }

    fun consume(context: RequestContext, pending: PendingMarketScreenshot): Boolean =
        pending.expiresAtMillis > System.currentTimeMillis() &&
                redis.deleteValueIfMatches(key(context), pending.value)

    fun expire(context: RequestContext, pending: PendingMarketScreenshot): Boolean =
        pending.expiresAtMillis <= System.currentTimeMillis() &&
                redis.deleteValueIfMatches(key(context), pending.value)

    fun cancel(context: RequestContext) = redis.deleteKey(key(context))
    fun isBusy(context: RequestContext): Boolean = redis.hasKey(key(context) + ":busy")
    fun acquire(context: RequestContext): String? = UUID.randomUUID().toString().takeIf {
        redis.setValueIfAbsent(key(context) + ":busy", it, 300, TimeUnit.SECONDS)
    }

    fun release(context: RequestContext, token: String) = redis.deleteValueIfMatches(key(context) + ":busy", token)

    private fun key(context: RequestContext): String {
        val identity =
            listOf(context.platform, context.botId, context.messageType, context.groupId.orEmpty(), context.userId)
                .joinToString("") { "${it.length}:$it" }
        val hash = MessageDigest.getInstance("SHA-256").digest(identity.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return "Wuliang:Warframe:market-screenshot:waiting:$hash"
    }
}