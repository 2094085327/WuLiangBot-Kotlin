package bot.wuliang.riven

import bot.wuliang.config.WfMarketConfig.WF_RIVEN_RESULT_KEY_PREFIX
import bot.wuliang.entity.vo.WfMarketVo
import bot.wuliang.redis.RedisService
import org.springframework.stereotype.Component
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * 按请求 UUID 保存紫卡查询结果。结果只服务于短暂的截图渲染流程，过期后由 Redis 自动清理。
 */
@Component
class RivenAuctionResultStore(
    private val redisService: RedisService,
) {
    fun publish(value: WfMarketVo.RivenOrderList): UUID {
        val resultId = UUID.randomUUID()
        redisService.setValueWithExpiry(
            key(resultId),
            value,
            RESULT_TTL_MINUTES,
            TimeUnit.MINUTES,
        )
        return resultId
    }

    fun get(resultId: UUID): WfMarketVo.RivenOrderList? =
        redisService.getValueTyped<WfMarketVo.RivenOrderList>(key(resultId))

    private fun key(resultId: UUID): String = WF_RIVEN_RESULT_KEY_PREFIX + resultId

    companion object {
        private const val RESULT_TTL_MINUTES = 5L
    }
}