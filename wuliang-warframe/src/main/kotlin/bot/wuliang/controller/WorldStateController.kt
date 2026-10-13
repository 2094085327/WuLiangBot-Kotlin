package bot.wuliang.controller

import bot.wuliang.exception.RespBean
import bot.wuliang.translation.PublicExportService
import bot.wuliang.worldstate.WorldPanel
import bot.wuliang.worldstate.WorldStatePanel
import bot.wuliang.worldstate.WorldStateSource
import bot.wuliang.worldstate.WorldStateUnavailableException
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

/** 后台世界状态接口，复用现有 Warframe 管理接口的登录拦截器。 */
@RestController
class WorldStateController(private val source: WorldStateSource, private val translations: PublicExportService) {
    @GetMapping("/warframe/wfManage/worldState")
    fun worldState(): RespBean<WorldPanel> = try {
        val index = translations.index()
        val panel = WorldStatePanel(
            index, translations::name, translations::description,
            conquestLookup = { key ->
                translations.conquestInfo(key).let { listOfNotNull(it.value, it.desc).joinToString("：") }
            },
            calendarLookup = { key ->
                translations.calendarUpgradeInfo(key).let { listOfNotNull(it.value, it.desc).joinToString("：") }
            })
        RespBean.success(panel.parse(source.snapshot()))
    } catch (error: WorldStateUnavailableException) {
        RespBean.error(message = error.message.orEmpty())
    }
}