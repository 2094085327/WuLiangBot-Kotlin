package bot.demo.txbot.game.lifeRestart

import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.RequestMapping


/**
 * @description: 人生重开模板数据
 * @author Nature Zero
 * @date 2024/2/24 14:15
 */
@Controller
class LifeRestartController {
    @RequestMapping("/lifeRestart")
    fun lifeRestart(model: Model, userId: String): String {
        val userList = LifeRestartMain.userList
        userList.find { it.userId == userId }.let { userInfo ->
            model.addAttribute("property", userInfo?.property)

        }

        val sendList = LifeRestartMain.sendStrList

        sendList.find { sendMap ->
            sendMap["userId"] == userId
        }.let { sendMap ->
            val sendStr = sendMap?.get("sendStr") as MutableList<*>
            model.addAttribute("sendStr", sendStr)
        }

        return "LifeRestart/LifeRestartMain"
    }

    @RequestMapping("/lifeRestartTalent")
    @Suppress("UNCHECKED_CAST")
    fun talent(model: Model, userId: String): String {
        val userList = LifeRestartMain.userList
        userList.find { it.userId == userId }.let { userInfo ->
            model.addAttribute("talentDataVo", userInfo?.randomTalentTemp as List<TalentDataVo>)
        }

        return "LifeRestart/LifeRestartTalent"
    }
}