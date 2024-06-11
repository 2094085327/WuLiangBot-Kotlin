package bot.demo.txbot.warframe

import bot.demo.txbot.warframe.WfStatusController.WfStatus.archonHuntEntity
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
}