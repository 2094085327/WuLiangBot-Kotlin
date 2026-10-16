package bot.wuliang.api

import bot.wuliang.config.WfMarketConfig.WF_VOIDTRADER_KEY
import bot.wuliang.exception.RespBean
import bot.wuliang.moudles.VoidTrader
import bot.wuliang.redis.RedisService
import bot.wuliang.translation.PublicExportService
import bot.wuliang.utils.TimeUtils.formatDuration
import bot.wuliang.utils.TimeUtils.getInstantNow
import bot.wuliang.worldstate.service.TraderQueryService
import bot.wuliang.worldstate.source.WorldStateSource
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.time.Duration

/** 原有虚空商人页面接口，库存查询使用共用商人解析器。 */
@Api(tags = ["Warframe接口"])
@RestController
@RequestMapping("/warframe")
class WarframeTraderController(
    private val traderQueryService: TraderQueryService,
    private val worldStateSource: WorldStateSource,
    private val translations: PublicExportService,
    private val redisService: RedisService,
) {

    /** 读取商人的到访与库存信息；缓存缺失时刷新数据，返回前更新距离离开时间的倒计时。 */
    @ApiOperation("虚空商人信息")
    @GetMapping("/voidTrader")
    fun voidTrader(): RespBean<out List<VoidTrader>> {
        if (!redisService.hasKey(translations.cacheKey(WF_VOIDTRADER_KEY))) {
            val data = worldStateSource.get()
            traderQueryService.getVoidTraders(data["VoidTraders"])
        }
        val voidTraderList = redisService.getValueTyped<List<VoidTrader>>(translations.cacheKey(WF_VOIDTRADER_KEY))
            ?: return RespBean.error()

        val result = voidTraderList
            .onEach { fissure ->
                fissure.eta = formatDuration(Duration.between(getInstantNow(), fissure.expiry))
            }

        return RespBean.success(result)
    }
}