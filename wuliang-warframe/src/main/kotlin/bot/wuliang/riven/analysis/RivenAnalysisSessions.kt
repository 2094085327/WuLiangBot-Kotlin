package bot.wuliang.riven.analysis

import bot.wuliang.adapter.context.RequestContext
import bot.wuliang.redis.RedisService
import org.springframework.stereotype.Component
import java.security.MessageDigest
import java.util.*
import java.util.concurrent.TimeUnit

data class PendingRivenImage(val value: String, val rank: Int?) {
    val expiresAtMillis: Long get() = value.substringBefore(':').toLong()
}

/** 等待会话同时隔离平台、机器人、用户及会话。 */
@Component
class RivenAnalysisSessions(private val redis: RedisService) {
    fun begin(context: RequestContext, rank: Int?): PendingRivenImage {
        val pending =
            PendingRivenImage("${System.currentTimeMillis() + 60_000}:${rank ?: ""}:${UUID.randomUUID()}", rank)
        // 接图期限仍为一分钟；记录多保留一段时间，供超时任务原子确认本轮等待尚未被消费或替换。
        redis.setValueWithExpiry(key(context), pending.value, 300, TimeUnit.SECONDS)
        return pending
    }

    fun pending(context: RequestContext): PendingRivenImage? {
        val value = redis.getValueOrThrow(key(context)) as? String ?: return null
        val parts = value.split(':', limit = 3)
        if ((parts.firstOrNull()?.toLongOrNull() ?: 0) <= System.currentTimeMillis()) return null
        return PendingRivenImage(value, parts.getOrNull(1)?.toIntOrNull())
    }

    fun consume(context: RequestContext, pending: PendingRivenImage): Boolean =
        // 读取后仍校验截止时间；Lua 比较删除避免并发图片消费同一会话。
        pending.expiresAtMillis > System.currentTimeMillis() &&
                redis.deleteValueIfMatches(key(context), pending.value)

    fun expire(context: RequestContext, pending: PendingRivenImage): Boolean =
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
        return "Wuliang:Warframe:riven-analysis:waiting:$hash"
    }
}