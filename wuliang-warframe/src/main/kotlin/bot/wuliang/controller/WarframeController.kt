package bot.wuliang.controller

import bot.wuliang.entity.WfOtherNameEntity
import bot.wuliang.entity.vo.WfMarketVo
import bot.wuliang.entity.vo.WfStatusVo
import bot.wuliang.exception.RespBean
import bot.wuliang.redis.RedisService
import bot.wuliang.respEnum.WarframeRespEnum
import bot.wuliang.service.WfLexiconService
import bot.wuliang.service.WfRivenService
import bot.wuliang.utils.WfUtil
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.concurrent.TimeUnit


/**
 * @description: Warframe控制类
 * @author Nature Zero
 * @date 2024/6/11 下午1:23
 */
@RestController
@RequestMapping("/warframe")
class WarframeController(
    @Autowired private val wfLexiconService: WfLexiconService,
    @Autowired private val wfRivenService: WfRivenService,
    @Autowired private val redisService: RedisService,
    @Autowired private val wfUtil: WfUtil
) {
    /**
     * 临时的别名管理页面
     */
    @PostMapping("/wfManage/setOtherName")
    fun login(
        @RequestParam("itemName") itemName: String,
        @RequestParam("otherName") otherName: String,
    ): RespBean {
        wfLexiconService.insertOtherName(itemName, otherName)
        redisService.deleteKey("warframe:allOtherName")
        return RespBean.success(WarframeRespEnum.SUBMIT_SUCCESS)
    }

    @RequestMapping("/archonHunt")
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
    fun fissureList(@RequestParam("type") type: String): WfStatusVo.FissureList? {
        return redisService.getValueTyped<WfStatusVo.FissureList>("warframe:${type}")
    }

    @RequestMapping("/voidTrader")
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
    fun lich(
        @RequestParam("url_name") urlName: String?,
        @RequestParam("damage") damage: String?,
        @RequestParam("element") element: String?,
        @RequestParam("ephemera") ephemera: String?
    ): WfMarketVo.LichEntity? {
        return redisService.getValueTyped<WfMarketVo.LichEntity>(
            "warframe:lichOrderEntity:${urlName}${damage}${element}${ephemera}",
        )
    }

    @RequestMapping("/riven")
    fun riven(): WfMarketVo.RivenOrderList? {
        return WfMarketController.WfMarket.rivenOrderList
    }

    @RequestMapping("/nightWave")
    fun nightWave(): WfStatusVo.NightWaveEntity? {
        return redisService.getValueTyped<WfStatusVo.NightWaveEntity>("warframe:nightWave")
    }

    @RequestMapping("/invasions")
    fun invasions(): List<*> {
        return redisService.getValue("warframe:invasions") as List<*>
    }

    @RequestMapping("/incarnon")
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

    @RequestMapping("/incarnonRiven")
    @Suppress("UNCHECKED_CAST")
    fun incarnonRiven(): Map<String, String>? {
        return redisService.getValue("warframe:incarnonRiven") as Map<String, String>?
    }

    @RequestMapping("/spirals")
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

    @RequestMapping("/allOtherName")
    @Suppress("UNCHECKED_CAST")
    fun allOtherName(): RespBean {
        if (redisService.getValue("warframe:allOtherName") == null) {
            val allOtherName = wfLexiconService.selectAllOtherName()
            redisService.setValue("warframe:allOtherName", allOtherName)
            return RespBean.success(allOtherName)
        } else return RespBean.success(redisService.getValue("warframe:allOtherName") as List<WfOtherNameEntity>)
    }

    @RequestMapping("/deleteOtherName")
    fun deleteOtherName(@RequestParam("other_name_id") id: Int): RespBean {
        try {
            wfLexiconService.deleteOtherName(id)
            redisService.deleteKey("warframe:allOtherName")
            return RespBean.success()
        } catch (e: Exception) {
            return RespBean.error(WarframeRespEnum.DELETE_OTHER_NAME_ERROR)
        }
    }

    @RequestMapping("/updateOtherName")
    fun updateOtherName(
        @RequestParam("other_name_id") id: Int,
        @RequestParam("other_name") otherName: String
    ): RespBean {
        try {
            wfLexiconService.updateOtherName(id, otherName)
            redisService.deleteKey("warframe:allOtherName")
            return RespBean.success()
        } catch (e: Exception) {
            return RespBean.error(WarframeRespEnum.UPDATE_OTHER_NAME_ERROR)
        }
    }


    @RequestMapping("/allRivenPrice")
    @Suppress("UNCHECKED_CAST")
    fun allRivenPrice(): Any? {
        return redisService.getValue("warframe:rivenRanking") as List<WfMarketVo.RivenRank>
    }
}