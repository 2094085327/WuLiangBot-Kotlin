package bot.demo.txbot.warframe

import bot.demo.txbot.warframe.WfStatusController.WfStatus
import bot.demo.txbot.warframe.WfStatusController.WfStatus.archonHuntEntity
import bot.demo.txbot.warframe.WfStatusController.WfStatus.sortieEntity
import bot.demo.txbot.warframe.WfStatusController.WfStatus.steelPathEntity
import bot.demo.txbot.warframe.database.WfLexiconService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody


/**
 * @description: Warframe控制类
 * @author Nature Zero
 * @date 2024/6/11 下午1:23
 */
@Controller
@RequestMapping("/warframe")
class WarframeController(
    @Value("\${wuLiang.config.userName}") val manageUserName: String,
    @Value("\${wuLiang.config.password}") val managePassword: String,
    @Autowired val wfLexiconService: WfLexiconService
) {
    /**
     * 临时的别名管理页面
     */
    @RequestMapping("/wfManage/page")
    fun index(model: Model): String {
        return "Warframe/WfManage"
    }

    /**
     * 临时的别名管理页面
     */
    @ResponseBody
    @PostMapping("/wfManage/login")
    fun login(
        @RequestParam("username") username: String,
        @RequestParam("password") password: String,
        @RequestParam("itemName") itemName: String,
        @RequestParam("otherName") otherName: String,
    ): String {
        if (username == manageUserName && password == managePassword) {
            wfLexiconService.insertOtherName(itemName, otherName)
            return "success"
        } else return "error"
    }

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

    @RequestMapping("/spirals")
    fun spirals(model: Model): String {
        val moodSpiralsEntity = WfStatus.moodSpiralsEntity
        moodSpiralsEntity.let { model.addAttribute("moodSpiralsEntity", moodSpiralsEntity) }
        return "Warframe/WfMoodSpirals"
    }
}