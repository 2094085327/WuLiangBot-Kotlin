package bot.wuliang.controller

import bot.wuliang.utils.GachaLogUtil
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
class GachaLogController(private val gachaLogUtil: GachaLogUtil) {
    @RequestMapping("/gachaLog")
    fun gacha(model: Model): String {
        val gachaData = gachaLogUtil.getGachaData()
        val permanents = gachaData.permanents
        val roles = gachaData.roles
        val weapons = gachaData.weapons
        val mixPools = gachaData.mixPool

        val roleCount = gachaData.roleCount
        val weaponCount = gachaData.weaponCount
        val permanentCount = gachaData.permanentCount
        val mixPoolCount = gachaData.mixCount

        model.addAttribute("permanents", permanents)
        model.addAttribute("roles", roles)
        model.addAttribute("weapons", weapons)
        model.addAttribute("mixPools", mixPools)
        model.addAttribute("roleCount", roleCount)
        model.addAttribute("weaponCount", weaponCount)
        model.addAttribute("permanentCount", permanentCount)
        model.addAttribute("mixPoolCount", mixPoolCount)
        return "GenShin/GachaLog"
    }
}