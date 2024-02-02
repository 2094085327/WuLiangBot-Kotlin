package bot.demo.txbot.genShin.database.gachaLog

import bot.demo.txbot.genShin.util.MysDataUtil
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
        val gachaData = MysDataUtil().getGachaData()
        val permanents = gachaData.permanents
        val roles = gachaData.roles
        val weapons = gachaData.weapons

        val data = listOf(permanents, roles, weapons)

        data.forEach { itemList ->
            val remainingItems = if (itemList.size % 6 != 0) 6 - itemList.size % 6 else 0
            repeat(remainingItems) {
                itemList.add(HtmlEntity(null, null, null, null, null, null, null))
            }
        }

        model.addAttribute("permanents", permanents)
        model.addAttribute("roles", roles)
        model.addAttribute("weapons", weapons)
        return "GenShin/GachaLog"
    }
}