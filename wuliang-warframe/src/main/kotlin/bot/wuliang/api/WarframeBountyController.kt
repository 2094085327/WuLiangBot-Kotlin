package bot.wuliang.api

import bot.wuliang.exception.RespBean
import bot.wuliang.translation.PublicExportService
import bot.wuliang.worldstate.model.BountyGroup
import bot.wuliang.worldstate.model.BountyReport
import bot.wuliang.worldstate.service.BountyQueryService
import bot.wuliang.worldstate.source.WorldStateUnavailableException
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

/** 只读赏金截图数据接口，复用机器人查询缓存。 */
@RestController
class WarframeBountyController(
    private val query: BountyQueryService,
    private val translations: PublicExportService,
) {
    @GetMapping("/warframe/bounties")
    fun bounties(@RequestParam(defaultValue = "all") type: String): RespBean<BountyReport> {
        if (type != "all" && BountyGroup.entries.none { it.id == type }) {
            return RespBean.error(message = "不支持的赏金类型")
        }
        if (!translations.ready) return RespBean.error(message = PublicExportService.UNAVAILABLE_MESSAGE)
        return try {
            val report = query.getBounties()
            RespBean.success(if (type == "all") report else report.copy(groups = report.groups.filter { it.id == type }))
        } catch (error: WorldStateUnavailableException) {
            RespBean.error(message = error.message.orEmpty())
        }
    }
}