package bot.wuliang.controller

import bot.wuliang.config.WfMarketConfig.WF_ALL_OTHER_NAME_KEY
import bot.wuliang.config.WfMarketConfig.WF_ARCHONHUNT_KEY
import bot.wuliang.config.WfMarketConfig.WF_INVASIONS_KEY
import bot.wuliang.config.WfMarketConfig.WF_LICHORDER_KEY
import bot.wuliang.config.WfMarketConfig.WF_MARKET_CACHE_KEY
import bot.wuliang.config.WfMarketConfig.WF_NIGHTWAVE_KEY
import bot.wuliang.config.WfMarketConfig.WF_SORTIE_KEY
import bot.wuliang.config.WfMarketConfig.WF_STEELPATH_KEY
import bot.wuliang.config.WfMarketConfig.WF_VOIDTRADER_KEY
import bot.wuliang.entity.WfOtherNameEntity
import bot.wuliang.entity.vo.WfMarketVo
import bot.wuliang.entity.vo.WfStatusVo
import bot.wuliang.exception.RespBean
import bot.wuliang.exception.RespBeanEnum
import bot.wuliang.httpUtil.HttpUtil
import bot.wuliang.httpUtil.ProxyUtil
import bot.wuliang.httpUtil.entity.ProxyInfo
import bot.wuliang.redis.RedisService
import bot.wuliang.respEnum.WarframeRespEnum
import bot.wuliang.service.WfLexiconService
import bot.wuliang.service.WfRivenService
import bot.wuliang.utils.WfUtil
import io.swagger.annotations.Api
import io.swagger.annotations.ApiOperation
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.*
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.Socket
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
    @Autowired private val wfRivenService: WfRivenService,
    @Autowired private val redisService: RedisService,
    @Autowired private val wfUtil: WfUtil
) {
    @Autowired
    private lateinit var proxyUtil: ProxyUtil

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

    /**
     * 新增别名
     */
    @GetMapping("/test")
    fun test(): RespBean {
        // 调用 proxyMain 获取代理列表
//        val proxyList = redisService.getValueTyped<List<ProxyInfo>>("Wuliang:http:proxy")
//
//        // 检查 proxyList 是否为空或无效
//        if (proxyList.isNullOrEmpty()) {
//            return RespBean.error(RespBeanEnum.ERROR)
//        }

        // 将 ProxyInfo 列表转换为 Proxy 对象列表
//        val proxies = proxyList.map { proxyInfo ->
//            Proxy(Proxy.Type.HTTP, InetSocketAddress(proxyInfo.ip, proxyInfo.port!!))
//        }
        val proxy = Proxy(Proxy.Type.SOCKS, InetSocketAddress("219.154.210.157", 9999))

//        try {
//            val socket = Socket(proxy)
//            socket.connect(InetSocketAddress("api.warframe.market", 443), 10000)
//            println("Connected successfully through SOCKS proxy.")
//            socket.close()
//        } catch (e: Exception) {
//            e.printStackTrace()
//        }
        // 使用随机代理发起请求
        return RespBean.success(
            HttpUtil.doGetStr(
                "https://api.warframe.market/v1/items/111/orders",
//                headers = wfUtil.generateRandomHeaders(),
                proxy = proxy
            )
        )
    }


    @ApiOperation("执刑官数据")
    @RequestMapping("/archonHunt")
    fun archonHunt(): WfStatusVo.ArchonHuntEntity? {
        // 访问此链接时Redis必然存在缓存，直接从Redis中获取数据
        var (expiry, archonHuntEntity) = redisService.getExpireAndValue(WF_ARCHONHUNT_KEY)
        if (expiry == null) expiry = -1L
        archonHuntEntity as WfStatusVo.ArchonHuntEntity
        // 更新时间为当前时间（秒）
        archonHuntEntity.eta = wfUtil.formatTimeBySecond(expiry)
        redisService.setValueWithExpiry(
            WF_ARCHONHUNT_KEY,
            archonHuntEntity,
            expiry,
            TimeUnit.SECONDS
        )
        return archonHuntEntity
    }

    @ApiOperation("每日突击数据")
    @RequestMapping("/sortie")
    fun sortie(): WfStatusVo.SortieEntity? {
        // 访问此链接时Redis必然存在缓存，直接从Redis中获取数据
        var (expiry, sortieEntity) = redisService.getExpireAndValue(WF_SORTIE_KEY)
        if (expiry == null) expiry = -1L
        sortieEntity as WfStatusVo.SortieEntity
        // 更新时间为当前时间（秒）
        sortieEntity.eta = wfUtil.formatTimeBySecond(expiry)

        redisService.setValueWithExpiry(
            WF_SORTIE_KEY,
            sortieEntity,
            expiry,
            TimeUnit.SECONDS
        )
        return sortieEntity
    }

    @ApiOperation("钢路奖励")
    @RequestMapping("/steelPath")
    fun steelPath(): WfStatusVo.SteelPathEntity? {
        // 访问此链接时Redis必然存在缓存，直接从Redis中获取数据
        var (expiry, steelPathEntity) = redisService.getExpireAndValue(WF_STEELPATH_KEY)
        if (expiry == null) expiry = -1L
        steelPathEntity as WfStatusVo.SteelPathEntity
        // 更新时间为当前时间（秒）
        steelPathEntity.remaining = wfUtil.formatTimeBySecond(expiry)
        redisService.setValueWithExpiry(
            WF_STEELPATH_KEY,
            steelPathEntity,
            expiry,
            TimeUnit.SECONDS
        )
        return steelPathEntity
    }

    @ApiOperation("裂缝信息")
    @RequestMapping("/fissureList")
    fun fissureList(@RequestParam("type") type: String): WfStatusVo.FissureList? {
        return redisService.getValueTyped<WfStatusVo.FissureList>(WF_MARKET_CACHE_KEY + type)
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
    fun nightWave(): WfStatusVo.NightWaveEntity? {
        return redisService.getValueTyped<WfStatusVo.NightWaveEntity>(WF_NIGHTWAVE_KEY)
    }

    @ApiOperation("入侵信息")
    @RequestMapping("/invasions")
    fun invasions(): List<WfStatusVo.IncarnonEntity>? {
        return redisService.getValueTyped<List<WfStatusVo.IncarnonEntity>>(WF_INVASIONS_KEY)
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