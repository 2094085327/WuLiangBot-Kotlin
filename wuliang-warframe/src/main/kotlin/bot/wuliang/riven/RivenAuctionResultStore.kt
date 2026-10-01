package bot.wuliang.riven

import bot.wuliang.config.WfMarketConfig.WF_RIVEN_RESULT_KEY_PREFIX
import bot.wuliang.entity.vo.WfMarketVo
import bot.wuliang.redis.RedisService
import bot.wuliang.translation.PublicExportService
import org.springframework.stereotype.Component
import java.util.*
import java.util.concurrent.TimeUnit

/**
 * 按请求 UUID 保存紫卡查询结果。结果只服务于短暂的截图渲染流程，过期后由 Redis 自动清理。
 */
@Component
class RivenAuctionResultStore(
    private val redisService: RedisService,
    private val translations: PublicExportService,
) {
    /**
     * 使用查询开始时捕获的版本前缀保存短期结果，防止进行中的旧查询写入新翻译版本。
     * @param generation 调用方捕获的完整结果缓存前缀，已包含翻译版本
     */
    fun publish(value: WfMarketVo.RivenOrderList, generation: String): UUID {
        val resultId = UUID.randomUUID()
        redisService.setValueWithExpiry(
            "$generation:$resultId",
            value,
            RESULT_TTL_MINUTES,
            TimeUnit.MINUTES,
        )
        return resultId
    }

    /**
     * 仅从当前翻译版本读取渲染结果；词库切换后，旧版本结果按过期时间自然清理。
     */
    fun get(resultId: UUID): WfMarketVo.RivenOrderList? =
        redisService.getValueTyped<WfMarketVo.RivenOrderList>(key(resultId))

    private fun key(resultId: UUID): String = translations.cacheKey(WF_RIVEN_RESULT_KEY_PREFIX) + ":" + resultId

    companion object {
        private const val RESULT_TTL_MINUTES = 5L
    }
}