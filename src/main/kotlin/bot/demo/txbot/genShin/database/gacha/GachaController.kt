package bot.demo.txbot.genShin.database.gacha

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
class GachaController {
    @RequestMapping("/gacha")
    fun gacha(model: Model): String {
        return "GenShin/GachaLog"
    }
}