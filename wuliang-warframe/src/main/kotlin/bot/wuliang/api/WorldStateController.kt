package bot.wuliang.api

import bot.wuliang.exception.RespBean
import bot.wuliang.translation.PublicExportService
import bot.wuliang.worldstate.WeaponRotationReport
import bot.wuliang.worldstate.WikiWeaponRotationSource
import bot.wuliang.worldstate.model.WorldPanel
import bot.wuliang.worldstate.presentation.WorldPanelAssembler
import bot.wuliang.worldstate.source.WorldStateSource
import bot.wuliang.worldstate.source.WorldStateUnavailableException
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/** 后台世界状态接口，复用现有 Warframe 管理接口的登录拦截器。 */
@RestController
class WorldStateController(
    private val source: WorldStateSource,
    private val translations: PublicExportService,
    private val wikiWeaponRotations: WikiWeaponRotationSource,
) {
    /** 与机器人共用 Wiki 获取缓存；来源与修订时间保留在响应中。 */
    @GetMapping("/warframe/wfManage/weaponRotations")
    fun weaponRotations(): RespBean<List<WeaponRotationReport>> = RespBean.success(
        wikiWeaponRotations.reports().map { report ->
            report.copy(weapons = report.weapons.map { offer ->
                offer.copy(displayName = if (translations.ready) translations.name(offer.name) else offer.name)
            })
        }
    )

    /**
     * 使用同一份官方快照与当前词库组装后台世界状态面板。
     *
     * @return 面板响应；没有可用快照时返回数据源提供的错误提示。
     */
    @GetMapping("/warframe/wfManage/worldState")
    fun worldState(): RespBean<WorldPanel> = try {
        val index = translations.index()
        val panel = WorldPanelAssembler(
            index, translations::name, translations::description,
            conquestLookup = { key ->
                translations.conquestInfo(key).let { listOfNotNull(it.value, it.desc).joinToString("：") }
            },
            calendarLookup = { key ->
                translations.calendarUpgradeInfo(key).let { listOfNotNull(it.value, it.desc).joinToString("：") }
            })
        RespBean.success(panel.assemble(source.snapshot()))
    } catch (error: WorldStateUnavailableException) {
        RespBean.error(message = error.message.orEmpty())
    }
}