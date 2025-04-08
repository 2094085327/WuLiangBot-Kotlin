package bot.wuliang.controller

import bot.wuliang.entity.UserInfoEntity
import bot.wuliang.redis.RedisService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController


/**
 * @description: 人生重开模板数据
 * @author Nature Zero
 * @date 2024/2/24 14:15
 */
@RestController
class LifeRestartController @Autowired constructor(
    private val redisService: RedisService
) {
    @RequestMapping("/lifeRestart")
    fun lifeRestart(@RequestParam("game_userId") userId: String): Pair<MutableMap<String, Int>, Any?> {
        val userInfo = redisService.getValueTyped<UserInfoEntity>("lifeRestart:userInfo:${userId}")
        val sendMessage = redisService.getValue("lifeRestart:sendMessage:${userId}")
        return Pair(userInfo?.property ?: mutableMapOf(), sendMessage)
    }

    @RequestMapping("/lifeRestartTalent")
    fun talent(@RequestParam("game_userId") userId: String): MutableList<bot.wuliang.entity.vo.TalentDataVo>? {
        return redisService.getValueTyped<UserInfoEntity>("lifeRestart:userInfo:${userId}")?.randomTalentTemp
    }


    @RequestMapping("/lifeRestartEndGame")
    fun lifeRestartEndGame(@RequestParam("game_userId") userId: String): Map<*, *> {
        return redisService.getValue("lifeRestart:endGame:${userId}") as Map<*, *>
    }
}