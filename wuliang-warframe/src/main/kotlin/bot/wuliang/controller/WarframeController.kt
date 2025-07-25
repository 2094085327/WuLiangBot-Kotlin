package bot.wuliang.controller

import bot.wuliang.config.WARFRAME_STATUS_URL
import bot.wuliang.config.WfMarketConfig.WF_ALL_OTHER_NAME_KEY
import bot.wuliang.config.WfMarketConfig.WF_ARCHONHUNT_KEY
import bot.wuliang.config.WfMarketConfig.WF_FISSURE_KEY
import bot.wuliang.config.WfMarketConfig.WF_INCARNON_KEY
import bot.wuliang.config.WfMarketConfig.WF_INCARNON_RIVEN_KEY
import bot.wuliang.config.WfMarketConfig.WF_INVASIONS_KEY
import bot.wuliang.config.WfMarketConfig.WF_LICHORDER_KEY
import bot.wuliang.config.WfMarketConfig.WF_MOODSPIRALS_KEY
import bot.wuliang.config.WfMarketConfig.WF_NIGHTWAVE_KEY
import bot.wuliang.config.WfMarketConfig.WF_SORTIE_KEY
import bot.wuliang.config.WfMarketConfig.WF_VOIDTRADER_KEY
import bot.wuliang.entity.WfOtherNameEntity
import bot.wuliang.entity.vo.WfMarketVo
import bot.wuliang.entity.vo.WfStatusVo
import bot.wuliang.exception.RespBean
import bot.wuliang.httpUtil.HttpUtil
import bot.wuliang.moudles.Fissure
import bot.wuliang.moudles.NightWave
import bot.wuliang.moudles.Sortie
import bot.wuliang.redis.RedisService
import bot.wuliang.respEnum.WarframeRespEnum
import bot.wuliang.service.WfLexiconService
import bot.wuliang.utils.ParseDataUtil
import bot.wuliang.utils.WfUtil
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit


/**
 * @description: Warframe控制类
 * @author Nature Zero
 * @date 2024/6/11 下午1:23
 */
@Api(tags = ["Warframe接口"])
@RestController
@RequestMapping("/warframe")
class WarframeController(
    @Autowired private val wfLexiconService: WfLexiconService,
    @Autowired private val redisService: RedisService,
    @Autowired private val wfUtil: WfUtil
) {
    @Autowired
    private lateinit var parseDataUtil: ParseDataUtil

    /**
     * 新增别名
     */
    @ApiOperation("新增别名")
    @PostMapping("/wfManage/setOtherName")
    fun addOtherName(
        @RequestParam("itemName") itemName: String,
        @RequestParam("otherName") otherName: String,
    ): RespBean {
        redisService.deleteKey(WF_ALL_OTHER_NAME_KEY)
        return RespBean.toReturn(wfLexiconService.insertOtherName(itemName, otherName))
    }

    @ApiOperation("执刑官数据")
    @RequestMapping("/archonHunt")
    fun archonHunt(): RespBean {
        // 访问此链接时Redis必然存在缓存，直接从Redis中获取数据
        var (expiry, archonHuntEntity) = redisService.getExpireAndValueTyped<Sortie>(WF_ARCHONHUNT_KEY)
        if (expiry == null) expiry = -1L
        // 更新时间为当前时间（秒）
        if (archonHuntEntity == null) return RespBean.error()
        archonHuntEntity.eta = wfUtil.formatTimeBySecond(expiry)

        return RespBean.success(archonHuntEntity)
    }

    @ApiOperation("每日突击数据")
    @RequestMapping("/sortie")
    fun sortie(): RespBean {
        // 访问此链接时Redis必然存在缓存，直接从Redis中获取数据
        var (expiry, sortieEntity) = redisService.getExpireAndValueTyped<Sortie>(WF_SORTIE_KEY)
        if (expiry == null) expiry = -1L
        // 更新时间为当前时间（秒）
        if (sortieEntity == null) return RespBean.error()
        sortieEntity.eta = wfUtil.formatTimeBySecond(expiry)

        return RespBean.success(sortieEntity)
    }

    @ApiOperation("钢路奖励")
    @RequestMapping("/steelPath")
    fun steelPath(): RespBean {
        var (expiry, steelPathEntity) = parseDataUtil.parseSteelPath()
        if (expiry == null) expiry = -1L
        steelPathEntity!!.eta = wfUtil.formatTimeBySecond(expiry)

        return RespBean.success(steelPathEntity)
    }

    @ApiOperation("裂缝信息")
    @RequestMapping("/fissureList")
    suspend fun fissureList(@RequestParam("type") type: String): RespBean {
        if (!redisService.hasKey(WF_FISSURE_KEY)) {
            val data = HttpUtil.doGetJson(WARFRAME_STATUS_URL)
            parseDataUtil.parseFissure(data["ActiveMissions"], data["VoidStorms"])
        }
        val fissureList = redisService.getValueTyped<List<Fissure?>>(WF_FISSURE_KEY)
            ?: return RespBean.error()

        val filterPredicate = when (type.lowercase()) {
            "ordinary" -> { fissure: Fissure -> fissure.storm != true && fissure.hard != true }
            "hard" -> { fissure: Fissure -> fissure.hard == true }
            "storm" -> { fissure: Fissure -> fissure.storm == true }
            else -> return RespBean.error()
        }

        val now = Instant.now()
        val result = fissureList
            .filterNotNull()
            .filter(filterPredicate)
            .onEach { fissure ->
                fissure.eta = wfUtil.formatDuration(Duration.between(now, fissure.expiry))
            }

        return RespBean.toReturn(result.size, result)
    }

    @ApiOperation("虚空商人信息")
    @RequestMapping("/voidTrader")
    fun voidTrader(): WfStatusVo.VoidTraderEntity? {
        // 访问此链接时Redis必然存在缓存，直接从Redis中获取数据
        var (expiry, voidTraderEntity) = redisService.getExpireAndValue(WF_VOIDTRADER_KEY)
        if (expiry == null) expiry = -1L
        voidTraderEntity as WfStatusVo.VoidTraderEntity
        // 更新时间为当前时间（秒）
        voidTraderEntity.time = wfUtil.formatTimeBySecond(expiry)

        redisService.setValueWithExpiry(
            WF_VOIDTRADER_KEY,
            voidTraderEntity,
            expiry,
            TimeUnit.SECONDS
        )
        return voidTraderEntity
    }

    @ApiOperation("玄骸武器信息")
    @RequestMapping("/lich")
    fun lich(
        @RequestParam("url_name") urlName: String?,
        @RequestParam("damage") damage: String?,
        @RequestParam("element") element: String?,
        @RequestParam("ephemera") ephemera: String?
    ): WfMarketVo.LichEntity? {
        return redisService.getValueTyped<WfMarketVo.LichEntity>(WF_LICHORDER_KEY + "${urlName}${damage}${element}${ephemera}")
    }

    @ApiOperation("紫卡信息")
    @RequestMapping("/riven")
    fun riven(): WfMarketVo.RivenOrderList? {
        return WfMarketController.WfMarket.rivenOrderList
    }

    @ApiOperation("电波信息")
    @RequestMapping("/nightWave")
    fun nightWave(): RespBean {
        val nightWaveEntity = redisService.getValueTyped<NightWave>(WF_NIGHTWAVE_KEY) ?: return RespBean.error()
        nightWaveEntity.eta = wfUtil.formatTimeBySecond(Duration.between(Instant.now(), nightWaveEntity.expiry).seconds)
        nightWaveEntity.startTime =
            wfUtil.formatTimeBySecond(Duration.between(nightWaveEntity.activation, Instant.now()).seconds)

        return RespBean.success(nightWaveEntity)
    }

    @ApiOperation("入侵信息")
    @RequestMapping("/invasions")
    @Suppress("UNCHECKED_CAST")
    fun invasions(): RespBean {
        val invasions = redisService.getValue(WF_INVASIONS_KEY) as List<WfStatusVo.IncarnonEntity>
        return RespBean.toReturn(invasions.size, invasions)
    }

    @RequestMapping("/incarnon")
    fun incarnon(): WfStatusVo.IncarnonEntity? {
        var (expiry, incarnonEntity) = redisService.getExpireAndValue(WF_INCARNON_KEY)
        if (expiry == null) expiry = -1L
        incarnonEntity as WfStatusVo.IncarnonEntity
        // 更新时间为当前时间（秒）
        incarnonEntity.remainTime = wfUtil.formatTimeBySecond(expiry)
        redisService.setValueWithExpiry(
            WF_INCARNON_KEY,
            incarnonEntity,
            expiry,
            TimeUnit.SECONDS
        )
        return incarnonEntity
    }

    @RequestMapping("/incarnonRiven")
    @Suppress("UNCHECKED_CAST")
    fun incarnonRiven(): Map<String, String>? {
        return redisService.getValue(WF_INCARNON_RIVEN_KEY) as Map<String, String>?
    }

    @RequestMapping("/spirals")
    fun spirals(): WfStatusVo.MoodSpiralsEntity? {
        var (expiry, moodSpiralsEntity) = redisService.getExpireAndValue(WF_MOODSPIRALS_KEY)
        if (expiry == null) expiry = -1L
        moodSpiralsEntity as WfStatusVo.MoodSpiralsEntity
        // 更新时间为当前时间（秒）
        moodSpiralsEntity.remainTime = wfUtil.formatTimeBySecond(expiry)
        redisService.setValueWithExpiry(
            WF_MOODSPIRALS_KEY,
            moodSpiralsEntity,
            expiry,
            TimeUnit.SECONDS
        )
        return moodSpiralsEntity
    }

    @RequestMapping("/allOtherName")
    @Suppress("UNCHECKED_CAST")
    fun allOtherName(): RespBean {
        if (redisService.getValue(WF_ALL_OTHER_NAME_KEY) == null) {
            val allOtherName = wfLexiconService.selectAllOtherName()
            redisService.setValue(WF_ALL_OTHER_NAME_KEY, allOtherName)
            return RespBean.success(allOtherName)
        } else return RespBean.success(redisService.getValue(WF_ALL_OTHER_NAME_KEY) as List<WfOtherNameEntity>)
    }

    @RequestMapping("/deleteOtherName")
    fun deleteOtherName(@RequestParam("other_name_id") id: Int): RespBean {
        try {
            wfLexiconService.deleteOtherName(id)
            redisService.deleteKey(WF_ALL_OTHER_NAME_KEY)
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
            redisService.deleteKey(WF_ALL_OTHER_NAME_KEY)
            return RespBean.success()
        } catch (e: Exception) {
            return RespBean.error(WarframeRespEnum.UPDATE_OTHER_NAME_ERROR)
        }
    }


    @RequestMapping("/allRivenPrice")
    fun allRivenPrice(): Any? {
        return redisService.getValueTyped<List<WfMarketVo.RivenRank>>("warframe:rivenRanking")
    }
}