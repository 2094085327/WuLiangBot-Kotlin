package bot.demo.txbot.warframe

import bot.demo.txbot.warframe.WfStatusController.WfStatus
import bot.demo.txbot.warframe.WfStatusController.WfStatus.archonHuntEntity
import bot.demo.txbot.warframe.WfStatusController.WfStatus.sortieEntity
import bot.demo.txbot.warframe.WfStatusController.WfStatus.steelPathEntity
import bot.demo.txbot.warframe.database.WfLexiconService
import bot.demo.txbot.warframe.vo.WfMarketVo
import bot.demo.txbot.warframe.vo.WfStatusVo
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
    @ResponseBody
    fun archonHunt(): WfStatusVo.ArchonHuntEntity? {
        return archonHuntEntity
    }

    @RequestMapping("/sortie")
    @ResponseBody
    fun sortie(): WfStatusVo.SortieEntity? {
        return sortieEntity
    }

    @RequestMapping("/steelPath")
    @ResponseBody
    fun steelPath(): WfStatusVo.SteelPathEntity? {
        return steelPathEntity
    }

    @RequestMapping("/fissureList")
    @ResponseBody
    fun fissureList(): WfStatusVo.FissureList? {
        return WfStatus.fissureList
    }

    @RequestMapping("/voidTrader")
    @ResponseBody
    fun voidTrader(): WfStatusVo.VoidTraderEntity? {
        return WfStatus.voidTraderEntity
    }

    @RequestMapping("/lich")
    @ResponseBody
    fun lich(): WfMarketVo.LichEntity? {
        return WfMarketController.WfMarket.lichOrderEntity
    }

    @RequestMapping("/riven")
    @ResponseBody
    fun riven(): WfMarketVo.RivenOrderList? {
        return WfMarketController.WfMarket.rivenOrderList
    }

    @RequestMapping("/nightWave")
    @ResponseBody
    fun nightWave(): WfStatusVo.NightWaveEntity? {
        return WfStatus.nightWaveEntity
    }

    @RequestMapping("/invasions")
    @ResponseBody
    fun invasions(): MutableList<WfStatusVo.InvasionsEntity> {
        return WfStatus.invasionsEntity
    }

    @RequestMapping("/incarnon")
    @ResponseBody
    fun incarnon(): WfStatusVo.IncarnonEntity? {
        return WfStatus.incarnonEntity
    }

    @RequestMapping("/spirals")
    @ResponseBody
    fun spirals(): WfStatusVo.MoodSpiralsEntity? {
        return WfStatus.moodSpiralsEntity
    }
}