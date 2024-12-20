package bot.demo.txbot.game.lifeRestart

import bot.demo.txbot.common.utils.RedisService
import bot.demo.txbot.game.lifeRestart.vo.TalentDataVo
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Controller
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
    @RequestMapping("/lifeRestart")
    @ResponseBody
    fun lifeRestart(@RequestParam("game_userId") userId: String): Pair<MutableMap<String, Int>, Any?> {
        val userInfo = redisService.getValue("lifeRestart:userInfo:${userId}", LifeRestartUtil.UserInfo::class.java)
        val sendMessage = redisService.getValue("lifeRestart:sendMessage:${userId}")
        return Pair(userInfo?.property ?: mutableMapOf(), sendMessage)
    }

    @RequestMapping("/lifeRestartTalent")
    @ResponseBody
    fun talent(@RequestParam("game_userId") userId: String): MutableList<TalentDataVo>? {
        return redisService.getValue("lifeRestart:userInfo:${userId}", LifeRestartUtil.UserInfo::class.java)?.randomTalentTemp
    }


    @RequestMapping("/lifeRestartEndGame")
    @ResponseBody
    fun lifeRestartEndGame(@RequestParam("game_userId") userId: String): Map<*, *> {
        return redisService.getValue("lifeRestart:endGame:${userId}") as Map<*,*>
    }
}