package bot.demo.txbot.game.lifeRestart

import bot.demo.txbot.common.utils.RedisService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Controller
import org.springframework.ui.Model
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody


/**
 * @description: 人生重开模板数据
 * @author Nature Zero
 * @date 2024/2/24 14:15
 */
@Controller
class LifeRestartController @Autowired constructor(
    private val redisService: RedisService
) {
//    @RequestMapping("/lifeRestart")
//    fun lifeRestart(model: Model, userId: String): String {
////        val userList = LifeRestartMain.userList
////        userList.find { it.userId == userId }.let { userInfo ->
////            model.addAttribute("property", userInfo?.property)
////
////        }
//
//        val userInfo = redisService.getValue("lifeRestart:userInfo:${userId}", LifeRestartUtil.UserInfo::class.java)
//        model.addAttribute("property", userInfo?.property)
//
////        val sendList = LifeRestartMain.sendStrList
////
////        sendList.find { sendMap ->
////            sendMap["userId"] == userId
////        }.let { sendMap ->
////            val sendStr = sendMap?.get("sendStr") as MutableList<*>
////            model.addAttribute("sendStr", sendStr)
////        }
//
//       val sendMessage = redisService.getValue("lifeRestart:sendMessage:${userId}" )
//        println(sendMessage)
//
//        sendMessage as List<Map<String,Any>>
//        println(sendMessage)
//
//        model.addAttribute("sendStr", sendMessage)
//
//        return "LifeRestart/LifeRestartMain"
//    }

    @RequestMapping("/lifeRestart")
    @ResponseBody
    fun lifeRestart(@RequestParam("game_userId") userId: String): Pair<LifeRestartUtil.UserInfo?, Any?> {
        val userInfo = redisService.getValue("lifeRestart:userInfo:${userId}", LifeRestartUtil.UserInfo::class.java)
        val sendMessage = redisService.getValue("lifeRestart:sendMessage:${userId}")
        return Pair(userInfo, sendMessage)
    }

    @RequestMapping("/lifeRestartTalent")
    @Suppress("UNCHECKED_CAST")
    fun talent(model: Model, userId: String): String {
//        val userList = LifeRestartMain.userList
//        userList.find { it.userId == userId }.let { userInfo ->
//            model.addAttribute("talentDataVo", userInfo?.randomTalentTemp as List<TalentDataVo>)
//        }
        val userInfo = redisService.getValue("lifeRestart:userInfo:${userId}", LifeRestartUtil.UserInfo::class.java)
        model.addAttribute("talentDataVo", userInfo?.randomTalentTemp as List<TalentDataVo>)


        return "LifeRestart/LifeRestartTalent"
    }
}