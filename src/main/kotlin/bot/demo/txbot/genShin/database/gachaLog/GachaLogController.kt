package bot.demo.txbot.genShin.database.gachaLog

import bot.demo.txbot.genShin.util.GachaLogUtil
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.RequestMapping

/**
 *@Description:
 *@Author zeng
 *@Date 2023/10/7 15:02
 *@User 86188
 */
@Controller
class GachaLogController {
    @RequestMapping("/gachaLog")
    fun gacha(model: Model): String {
        val gachaData = GachaLogUtil().getGachaData()
        val permanents = gachaData.permanents
        val roles = gachaData.roles
        val weapons = gachaData.weapons

        val roleCount = gachaData.roleCount
        val weaponCount = gachaData.weaponCount
        val permanentCount = gachaData.permanentCount

        model.addAttribute("permanents", permanents)
        model.addAttribute("roles", roles)
        model.addAttribute("weapons", weapons)
        model.addAttribute("roleCount", roleCount)
        model.addAttribute("weaponCount", weaponCount)
        model.addAttribute("permanentCount", permanentCount)
        return "GenShin/GachaLog"
    }
}