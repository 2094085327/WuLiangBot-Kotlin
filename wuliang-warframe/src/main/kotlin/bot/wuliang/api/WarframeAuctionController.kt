package bot.wuliang.api

import bot.wuliang.config.WfMarketConfig.WF_LICHORDER_KEY
import bot.wuliang.entity.vo.WfMarketVo
import bot.wuliang.redis.RedisService
import bot.wuliang.riven.RivenAuctionResultStore
import bot.wuliang.translation.PublicExportService
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.*

/** 读取已缓存的玄骸与紫卡拍卖查询结果。 */
@Api(tags = ["Warframe接口"])
@RestController
@RequestMapping("/warframe")
class WarframeAuctionController(
    private val translations: PublicExportService,
    private val rivenAuctionResultStore: RivenAuctionResultStore,
    private val redisService: RedisService,
) {

    /**
     * 按武器、元素、幻纹和页码读取已经缓存的玄骸拍卖结果。
     *
     * @return 对应条件的查询结果；缓存不存在时返回 null。
     */
    @ApiOperation("玄骸武器信息")
    @RequestMapping("/lich")
    fun lich(
        @RequestParam("url_name") urlName: String?,
        @RequestParam("damage") damage: String?,
        @RequestParam("element") element: String?,
        @RequestParam("ephemera") ephemera: String?,
        @RequestParam("page", defaultValue = "1") page: Int,
    ): WfMarketVo.LichEntity? {
        val cacheKey =
            "${translations.cacheKey(WF_LICHORDER_KEY)}:${urlName}:${damage}:${element}:${ephemera}:page=$page"
        return redisService.getValueTyped<WfMarketVo.LichEntity>(cacheKey)
    }

    /**
     * 根据 [resultId] 读取已发布的紫卡拍卖查询结果。
     *
     * @return 查询结果；标识不存在或结果已过期时返回 null。
     */
    @ApiOperation("紫卡信息")
    @GetMapping("/riven")
    fun riven(@RequestParam("resultId") resultId: UUID): WfMarketVo.RivenOrderList? {
        return rivenAuctionResultStore.get(resultId)
    }
}