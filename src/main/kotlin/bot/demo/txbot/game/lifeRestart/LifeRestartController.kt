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
        println(userId)
        val userList = LifeRestartMain.userList
        println(userList)
        userList.find { it.userId == userId }.let { userInfo ->
            println(userInfo)
            model.addAttribute("property", userInfo?.property)

        }

        val sendList = LifeRestartMain.sendStrList
        println("sendList: $sendList")

        sendList.find { sendMap->
            sendMap["userId"] == userId
        }.let { sendMap ->
            println("sendMap?.get(sendStr): ${sendMap?.get("sendStr")}")
            val sendStr = sendMap?.get("sendStr") as MutableList<MutableList<Any>>
            println(sendStr)
            model.addAttribute("sendStr", sendStr)
        }

        return "LifeRestart/LifeRestartMain"
    }
}