package bot.wuliang.api

import bot.wuliang.config.WfMarketConfig.WF_RIVEN_REROLLED_KEY
import bot.wuliang.config.WfMarketConfig.WF_RIVEN_UN_REROLLED_KEY
import bot.wuliang.exception.RespBean
import bot.wuliang.moudles.Riven
import bot.wuliang.redis.RedisService
import bot.wuliang.riven.RivenRankingService
import bot.wuliang.translation.PublicExportService
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** 紫卡周榜接口，只负责筛选与排序展示。 */
@Api(tags = ["Warframe接口"])
@RestController
@RequestMapping("/warframe")
class WarframeRivenRankingController(
    private val rivenRankingService: RivenRankingService,
    private val translations: PublicExportService,
    private val redisService: RedisService,
) {

    /**
     * 筛选已洗或未洗紫卡周榜，按热度及中位价格排序；缺少缓存时先同步周榜。
     *
     * @param type 紫卡武器类型，为 null 时返回所有类型。
     * @param sort asc 为升序，desc 或其他值为降序。
     * @param rerolled 是否读取已洗紫卡分组。
     */
    @ApiOperation("紫卡排行榜数据")
    @GetMapping("/allRivenPrice")
    fun allRivenPrice(
        @RequestParam("type") type: String?,
        @RequestParam("sort") sort: String? = "desc",
        @RequestParam("rerolled") rerolled: Boolean = false
    ): RespBean<out List<Riven>> {
        if (!redisService.hasKey(translations.cacheKey(WF_RIVEN_UN_REROLLED_KEY)) || !redisService.hasKey(
                translations.cacheKey(
                    WF_RIVEN_REROLLED_KEY
                )
            )
        ) {
            rivenRankingService.refreshRanking()
        }

        val redisKey = if (rerolled) translations.cacheKey(WF_RIVEN_REROLLED_KEY) else translations.cacheKey(
            WF_RIVEN_UN_REROLLED_KEY
        )
        val rivenList = redisService.getValueTyped<List<Riven>>(redisKey) ?: return RespBean.error()
        val result = if (type != null) rivenList.filter { it.itemType == type } else rivenList

        val filteredResult = result.filter { it.median != null }

        val sortResult = when (sort ?: "desc") {
            "asc" -> filteredResult.sortedWith(compareBy({ it.pop ?: 0 }, { it.median }))
            "desc" -> filteredResult.sortedWith(compareByDescending<Riven> {
                it.pop ?: 0
            }.thenByDescending { it.median })

            else -> filteredResult.sortedWith(compareByDescending<Riven> { it.pop ?: 0 }.thenByDescending { it.median })
        }

        return RespBean.success(sortResult)
    }
}