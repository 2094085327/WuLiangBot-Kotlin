package bot.wuliang.worldstate.presentation

import bot.wuliang.imageProcess.WebImgUtil
import bot.wuliang.otherUtil.OtherUtil.STConversion.toMd5
import bot.wuliang.tencentCos.CosFileServiceImpl
import bot.wuliang.translation.PublicExportService
import bot.wuliang.worldstate.model.BountyGroup
import bot.wuliang.worldstate.service.BountyQueryService
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.stereotype.Service

/** 向截图页注入本次赏金快照；相同内容的别名查询复用同一张图片。 */
@Service
class BountyImageService(
    private val query: BountyQueryService,
    private val translations: PublicExportService,
    private val webImgUtil: WebImgUtil,
    private val cos: CosFileServiceImpl,
) {
    private val mapper = jacksonObjectMapper()

    fun imageUrl(group: BountyGroup? = null): String {
        val report = query.getBounties().let { result ->
            if (group == null) result else result.copy(groups = result.groups.filter { it.id == group.id })
        }
        // 截图只显示绝对刷新时间，避免图片缓存中的倒计时停滞。
        val fingerprint = "${translations.cacheTag()}:${report.groups}:${report.stale}".toMd5()
        cos.ensureLifecycleRule("bounties-", 1)
        return webImgUtil.getImgUrl(
            WebImgUtil.ImgData(
                url = "http://${webImgUtil.frontendAddress}/bounties?type=${group?.id ?: "all"}",
                data = mapper.writeValueAsString(report),
                imgName = "bounties-${group?.id ?: "all"}-$fingerprint",
                element = ".warframeBounties",
                waitElement = ".warframeBounties[data-ready='true']",
            )
        )
    }
}