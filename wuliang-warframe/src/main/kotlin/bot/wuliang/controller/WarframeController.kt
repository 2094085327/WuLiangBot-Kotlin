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
import bot.wuliang.config.WfMarketConfig.WF_RIVEN_REROLLED_KEY
import bot.wuliang.config.WfMarketConfig.WF_RIVEN_UN_REROLLED_KEY
import bot.wuliang.config.WfMarketConfig.WF_SIMARIS_KEY
import bot.wuliang.config.WfMarketConfig.WF_SORTIE_KEY
import bot.wuliang.config.WfMarketConfig.WF_VOIDTRADER_KEY
import bot.wuliang.entity.WfOtherNameEntity
import bot.wuliang.entity.vo.WfMarketVo
import bot.wuliang.entity.vo.WfStatusVo
import bot.wuliang.exception.RespBean
import bot.wuliang.httpUtil.HttpUtil
import bot.wuliang.moudles.*
import bot.wuliang.redis.RedisService
import bot.wuliang.respEnum.WarframeRespEnum
import bot.wuliang.service.WfLexiconService
import bot.wuliang.utils.ParseDataUtil
import bot.wuliang.utils.TimeUtils.formatDuration
import bot.wuliang.utils.TimeUtils.formatTimeBySecond
import bot.wuliang.utils.TimeUtils.getInstantNow
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Duration
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
    @GetMapping("/archonHunt")
    fun archonHunt(): RespBean {
        // 访问此链接时Redis必然存在缓存，直接从Redis中获取数据
        var (expiry, archonHuntEntity) = redisService.getExpireAndValueTyped<Sortie>(WF_ARCHONHUNT_KEY)
        if (expiry == null) expiry = -1L
        // 更新时间为当前时间（秒）
        if (archonHuntEntity == null) return RespBean.error()
        archonHuntEntity.eta = formatTimeBySecond(expiry)

        return RespBean.success(archonHuntEntity)
    }

    @ApiOperation("每日突击数据")
    @GetMapping("/sortie")
    fun sortie(): RespBean {
        // 访问此链接时Redis必然存在缓存，直接从Redis中获取数据
        var (expiry, sortieEntity) = redisService.getExpireAndValueTyped<Sortie>(WF_SORTIE_KEY)
        if (expiry == null) expiry = -1L
        // 更新时间为当前时间（秒）
        if (sortieEntity == null) return RespBean.error()
        sortieEntity.eta = formatTimeBySecond(expiry)

        return RespBean.success(sortieEntity)
    }

    @ApiOperation("钢路奖励")
    @GetMapping("/steelPath")
    fun steelPath(): RespBean {
        var (expiry, steelPathEntity) = parseDataUtil.parseSteelPath()
        if (expiry == null) expiry = -1L
        steelPathEntity!!.eta = formatTimeBySecond(expiry)

        return RespBean.success(steelPathEntity)
    }

    @ApiOperation("裂缝信息")
    @GetMapping("/fissureList")
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

        val now = getInstantNow()
        val result = fissureList
            .filterNotNull()
            .filter(filterPredicate)
            .onEach { fissure ->
                fissure.eta = formatDuration(Duration.between(now, fissure.expiry))
            }

        return RespBean.toReturn(result.size, result)
    }

    @ApiOperation("虚空商人信息")
    @GetMapping("/voidTrader")
    fun voidTrader(): RespBean {
        if (!redisService.hasKey(WF_VOIDTRADER_KEY)) {
            val data = HttpUtil.doGetJson(WARFRAME_STATUS_URL)
            parseDataUtil.parseVoidTraders(data["VoidTraders"])
        }
        val voidTraderList = redisService.getValueTyped<List<VoidTrader>>(WF_VOIDTRADER_KEY)
            ?: return RespBean.error()

        val result = voidTraderList
            .onEach { fissure ->
                fissure.eta = formatDuration(Duration.between(getInstantNow(), fissure.expiry))
            }

        return RespBean.success(result)
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
    @GetMapping("/nightWave")
    fun nightWave(): RespBean {
        val nightWaveEntity = redisService.getValueTyped<NightWave>(WF_NIGHTWAVE_KEY) ?: return RespBean.error()
        nightWaveEntity.eta = formatTimeBySecond(Duration.between(getInstantNow(), nightWaveEntity.expiry).seconds)
        nightWaveEntity.startTime =
            formatTimeBySecond(Duration.between(nightWaveEntity.activation, getInstantNow()).seconds)

        return RespBean.success(nightWaveEntity)
    }

    @ApiOperation("入侵信息")
    @GetMapping("/invasions")
    fun invasions(): RespBean {
        if (!redisService.hasKey(WF_INVASIONS_KEY)) {
            val data = HttpUtil.doGetJson(WARFRAME_STATUS_URL)
            parseDataUtil.parseInvasions(data["Invasions"])
        }

        val invasionsList = redisService.getValueTyped<List<Invasions>>(WF_INVASIONS_KEY)
            ?: return RespBean.error()

        return RespBean.toReturn(invasionsList.size, invasionsList)
    }

    @RequestMapping("/incarnon")
    fun incarnon(): WfStatusVo.IncarnonEntity? {
        var (expiry, incarnonEntity) = redisService.getExpireAndValue(WF_INCARNON_KEY)
        if (expiry == null) expiry = -1L
        incarnonEntity as WfStatusVo.IncarnonEntity
        // 更新时间为当前时间（秒）
        incarnonEntity.remainTime = formatTimeBySecond(expiry)
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

    @GetMapping("/spirals")
    fun spirals(): RespBean {
        var (expiry, moodSpiralsEntity) = redisService.getExpireAndValueTyped<MoodSpirals>(WF_MOODSPIRALS_KEY)
        if (expiry == null) expiry = -1L
        if (moodSpiralsEntity == null) return RespBean.error()
        // 更新时间为当前时间（秒）
        moodSpiralsEntity.remainTime = formatTimeBySecond(expiry)

        return RespBean.success(moodSpiralsEntity)
    }

    @GetMapping("/allOtherName")
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

    @ApiOperation("紫卡排行榜数据")
    @GetMapping("/allRivenPrice")
    fun allRivenPrice(
        @RequestParam("type") type: String?,
        @RequestParam("sort") sort: String? = "desc",
        @RequestParam("rerolled") rerolled: Boolean = false
    ): RespBean {
        if (!redisService.hasKey(WF_RIVEN_UN_REROLLED_KEY) || !redisService.hasKey(WF_RIVEN_REROLLED_KEY)) {
            parseDataUtil.parseWeeklyRiven()
        }

        val redisKey = if (rerolled) WF_RIVEN_REROLLED_KEY else WF_RIVEN_UN_REROLLED_KEY
        val rivenList = redisService.getValueTyped<List<Riven>>(redisKey) ?: return RespBean.error()
        val result = if (type != null) rivenList.filter { it.itemType == type } else rivenList

        val filteredResult = result.filter { it.median != null }

        val sortResult = when (sort ?: "desc") {
            "asc" -> filteredResult.sortedWith(compareBy({ it.pop ?: 0 }, { it.median }))
            "desc" -> filteredResult.sortedWith(compareByDescending<Riven> { it.pop ?: 0 }.thenByDescending { it.median })
            else -> filteredResult.sortedWith(compareByDescending<Riven> { it.pop ?: 0 }.thenByDescending { it.median })
        }


        return RespBean.success(sortResult)
    }

    @ApiOperation("圣殿结合仪式目标信息")
    @GetMapping("/simaris")
    fun simaris(): RespBean {
        if (!redisService.hasKey(WF_SIMARIS_KEY)) {
            val data = HttpUtil.doGetJson(WARFRAME_STATUS_URL)
            parseDataUtil.parseSimaris(data["LibraryInfo"])
        }
        val simarisEntity = redisService.getValueTyped<Simaris>(WF_SIMARIS_KEY)
            ?: return RespBean.error("圣殿结合仪式目标没有找到~")

        simarisEntity.eta = formatDuration(Duration.between(getInstantNow(), simarisEntity.expiry))

        return RespBean.success(simarisEntity)
    }

}