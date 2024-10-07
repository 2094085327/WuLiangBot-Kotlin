package bot.demo.txbot.warframe

import bot.demo.txbot.common.utils.RedisService
import bot.demo.txbot.warframe.database.WfLexiconService
import bot.demo.txbot.warframe.vo.WfMarketVo
import bot.demo.txbot.warframe.vo.WfStatusVo
import bot.demo.txbot.warframe.warframeResp.WarframeRespBean
import bot.demo.txbot.warframe.warframeResp.WarframeRespEnum
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody
import java.util.concurrent.TimeUnit


/**
 * @description: Warframe控制类
 * @author Nature Zero
 * @date 2024/6/11 下午1:23
 */
@Controller
@RequestMapping("/warframe")
class WarframeController(
    @Value("\${wuLiang.config.userName}") val manageUserName: String,
    @Value("\${wuLiang.config.password}") val managePassword: String,
    @Autowired val wfLexiconService: WfLexiconService,
    @Autowired private val redisService: RedisService,
    @Autowired private val wfUtil: WfUtil
) {
    /**
     * 临时的别名管理页面
     */
    @ResponseBody
    @PostMapping("/wfManage/login")
    fun login(
        @RequestParam("username") username: String,
        @RequestParam("password") password: String,
        @RequestParam("itemName") itemName: String,
        @RequestParam("otherName") otherName: String,
    ): WarframeRespBean {
        if (username == manageUserName && password == managePassword) {
            wfLexiconService.insertOtherName(itemName, otherName)
            return WarframeRespBean.info(WarframeRespEnum.SUBMIT_SUCCESS)
        } else return WarframeRespBean.error(WarframeRespEnum.SUBMIT_ERROR)
    }

    @RequestMapping("/archonHunt")
    @ResponseBody
    fun archonHunt(): WfStatusVo.ArchonHuntEntity? {
        // 访问此链接时Redis必然存在缓存，直接从Redis中获取数据
        var (expiry, archonHuntEntity) = redisService.getExpireAndValue("warframe:archonHunt")
        if (expiry == null) expiry = -1L
        archonHuntEntity as WfStatusVo.ArchonHuntEntity
        // 更新时间为当前时间（秒）
        archonHuntEntity.eta = wfUtil.formatTimeBySecond(expiry)
        redisService.setValueWithExpiry(
            "warframe:archonHunt",
            archonHuntEntity,
            expiry,
            TimeUnit.SECONDS
        )
        return archonHuntEntity
    }

    @RequestMapping("/sortie")
    @ResponseBody
    fun sortie(): WfStatusVo.SortieEntity? {
        // 访问此链接时Redis必然存在缓存，直接从Redis中获取数据
        var (expiry, sortieEntity) = redisService.getExpireAndValue("warframe:sortie")
        if (expiry == null) expiry = -1L
        sortieEntity as WfStatusVo.SortieEntity
        // 更新时间为当前时间（秒）
        sortieEntity.eta = wfUtil.formatTimeBySecond(expiry)

        redisService.setValueWithExpiry(
            "warframe:sortie",
            sortieEntity,
            expiry,
            TimeUnit.SECONDS
        )
        return sortieEntity
    }

    @RequestMapping("/steelPath")
    @ResponseBody
    fun steelPath(): WfStatusVo.SteelPathEntity? {
        // 访问此链接时Redis必然存在缓存，直接从Redis中获取数据
        var (expiry, steelPathEntity) = redisService.getExpireAndValue("warframe:steelPath")
        if (expiry == null) expiry = -1L
        steelPathEntity as WfStatusVo.SteelPathEntity
        // 更新时间为当前时间（秒）
        steelPathEntity.remaining = wfUtil.formatTimeBySecond(expiry)
        redisService.setValueWithExpiry(
            "warframe:steelPath",
            steelPathEntity,
            expiry,
            TimeUnit.SECONDS
        )
        return steelPathEntity
    }

    @RequestMapping("/fissureList")
    @ResponseBody
    fun fissureList(@RequestParam("type") type: String): WfStatusVo.FissureList? {
        return redisService.getValue("warframe:${type}", WfStatusVo.FissureList::class.java)
    }

    @RequestMapping("/voidTrader")
    @ResponseBody
    fun voidTrader(): WfStatusVo.VoidTraderEntity? {
        // 访问此链接时Redis必然存在缓存，直接从Redis中获取数据
        var (expiry, voidTraderEntity) = redisService.getExpireAndValue("warframe:voidTrader")
        if (expiry == null) expiry = -1L
        voidTraderEntity as WfStatusVo.VoidTraderEntity
        // 更新时间为当前时间（秒）
        voidTraderEntity.time = wfUtil.formatTimeBySecond(expiry)

        redisService.setValueWithExpiry(
            "warframe:voidTrader",
            voidTraderEntity,
            expiry,
            TimeUnit.SECONDS
        )
        return voidTraderEntity
    }

    @RequestMapping("/lich")
    @ResponseBody
    fun lich(): WfMarketVo.LichEntity? {
        return WfMarketController.WfMarket.lichOrderEntity
    }

    @RequestMapping("/riven")
    @ResponseBody
    fun riven(): WfMarketVo.RivenOrderList? {
        return WfMarketController.WfMarket.rivenOrderList
    }

    @RequestMapping("/nightWave")
    @ResponseBody
    fun nightWave(): WfStatusVo.NightWaveEntity? {
        return redisService.getValue("warframe:nightWave", WfStatusVo.NightWaveEntity::class.java)
    }

    @RequestMapping("/invasions")
    @ResponseBody
    fun invasions(): List<*> {
        return redisService.getValue("warframe:invasions") as List<*>
    }

    @RequestMapping("/incarnon")
    @ResponseBody
    fun incarnon(): WfStatusVo.IncarnonEntity? {
        var (expiry, incarnonEntity) = redisService.getExpireAndValue("warframe:incarnon")
        if (expiry == null) expiry = -1L
        incarnonEntity as WfStatusVo.IncarnonEntity
        // 更新时间为当前时间（秒）
        incarnonEntity.remainTime = wfUtil.formatTimeBySecond(expiry)
        redisService.setValueWithExpiry(
            "warframe:incarnon",
            incarnonEntity,
            expiry,
            TimeUnit.SECONDS
        )
        return incarnonEntity
    }

    @RequestMapping("/spirals")
    @ResponseBody
    fun spirals(): WfStatusVo.MoodSpiralsEntity? {
        var (expiry, moodSpiralsEntity) = redisService.getExpireAndValue("warframe:moodSpirals")
        if (expiry == null) expiry = -1L
        moodSpiralsEntity as WfStatusVo.MoodSpiralsEntity
        // 更新时间为当前时间（秒）
        moodSpiralsEntity.remainTime = wfUtil.formatTimeBySecond(expiry)
        redisService.setValueWithExpiry(
            "warframe:moodSpirals",
            moodSpiralsEntity,
            expiry,
            TimeUnit.SECONDS
        )
        return moodSpiralsEntity
    }
}