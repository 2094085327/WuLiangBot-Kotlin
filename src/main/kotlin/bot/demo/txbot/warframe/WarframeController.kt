package bot.demo.txbot.warframe

import bot.demo.txbot.warframe.WfStatusController.WfStatus
import bot.demo.txbot.warframe.WfStatusController.WfStatus.archonHuntEntity
import bot.demo.txbot.warframe.WfStatusController.WfStatus.sortieEntity
import bot.demo.txbot.warframe.WfStatusController.WfStatus.steelPathEntity
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.RequestMapping


/**
 * @description: Warframe控制类
 * @author Nature Zero
 * @date 2024/6/11 下午1:23
 */
@Controller
@RequestMapping("/warframe")
class WarframeController {

    @RequestMapping("/archonHunt")
    fun archonHunt(model: Model): String {
        val archonHuntEntity = archonHuntEntity
        if (archonHuntEntity != null) {
            model.addAttribute("archonHuntEntity", archonHuntEntity)
        }
        return "Warframe/WfArchonHunt"
    }

    @RequestMapping("/sortie")
    fun sortie(model: Model): String {
        val sortieEntity = sortieEntity
        if (sortieEntity != null) {
            model.addAttribute("sortieEntity", sortieEntity)
        }
        return "Warframe/WfSortie"
    }

    @RequestMapping("/steelPath")
    fun steelPath(model: Model): String {
        val sortieEntity = steelPathEntity
        if (sortieEntity != null) {
            model.addAttribute("steelPathEntity", steelPathEntity)
        }
        return "Warframe/WfSteelPath"
    }

    @RequestMapping("/fissureList")
    fun fissureList(model: Model): String {
        val fissure = WfStatus.fissureList
        if (fissure != null) {
            model.addAttribute("fissureList", fissure)
        }
        return "Warframe/WfFissureList"
    }

    @RequestMapping("/voidTrader")
    fun voidTrader(model: Model): String {
        val voidTrader = WfStatus.voidTraderEntity
        if (voidTrader != null) {
            model.addAttribute("voidTrader", voidTrader)
        }
        return "Warframe/WfVoidTrader"
    }

    @RequestMapping("/lich")
    fun lich(model: Model): String {
        val lichOrderEntity = WfMarketController.WfMarket.lichOrderEntity
        lichOrderEntity?.let { model.addAttribute("lichOrderEntity", lichOrderEntity) }
        return "Warframe/WfLich"
    }

    @RequestMapping("/riven")
    fun riven(model: Model): String {
        val rivenOrderList = WfMarketController.WfMarket.rivenOrderList
        rivenOrderList.let { model.addAttribute("rivenOrderList", rivenOrderList) }
        return "Warframe/WfRiven"
    }

    @RequestMapping("/nightWave")
    fun nightWave(model: Model): String {
        val nightWaveEntity = WfStatus.nightWaveEntity
        nightWaveEntity.let { model.addAttribute("nightWaveEntity", nightWaveEntity) }
        return "Warframe/WfNightWave"
    }

    @RequestMapping("/invasions")
    fun invasions(model: Model): String {
        val invasionsEntity = WfStatus.invasionsEntity
        invasionsEntity.let { model.addAttribute("invasionsEntity", invasionsEntity) }
        return "Warframe/WfInvasions"
    }

    @RequestMapping("/incarnon")
    fun incarnon(model: Model): String {
        val incarnonEntity = WfStatus.incarnonEntity
        incarnonEntity.let { model.addAttribute("incarnonEntity", incarnonEntity) }
        return "Warframe/WfIncarnon"
    }
}