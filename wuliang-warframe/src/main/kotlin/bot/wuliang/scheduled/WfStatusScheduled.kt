package bot.wuliang.scheduled

import bot.wuliang.config.WARFRAME_STATUS_ARCHON_HUNT
import bot.wuliang.config.WARFRAME_STATUS_STEEL_PATH
import bot.wuliang.config.WARFRAME_STATUS_VOID_TRADER
import bot.wuliang.config.WfMarketConfig.WF_ARCHONHUNT_KEY
import bot.wuliang.config.WfMarketConfig.WF_STEELPATH_KEY
import bot.wuliang.config.WfMarketConfig.WF_VOIDTRADER_KEY
import bot.wuliang.config.WfMarketConfig.WF_VOID_TRADER_COME_KEY
import bot.wuliang.entity.vo.WfStatusVo
import bot.wuliang.httpUtil.HttpUtil
import bot.wuliang.httpUtil.ProxyUtil
import bot.wuliang.otherUtil.OtherUtil.STConversion.turnZhHans
import bot.wuliang.redis.RedisService
import bot.wuliang.service.WfLexiconService
import bot.wuliang.utils.WfStatus.parseDuration
import bot.wuliang.utils.WfStatus.replaceFaction
import bot.wuliang.utils.WfStatus.replaceTime
import bot.wuliang.utils.WfUtil
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

@Component
class WfStatusScheduled {
    @Autowired
    private lateinit var proxyUtil: ProxyUtil

    @Autowired
    private lateinit var redisService: RedisService

    @Autowired
    private lateinit var wfUtil: WfUtil

    @Autowired
    private lateinit var wfLexiconService: WfLexiconService

    private val httpUtil = HttpUtil

    @Scheduled(cron = "${warframe.status.trader - cron}")
    fun getVoidTraderData(): WfStatusVo.VoidTraderEntity? {
        // 当Redis中商人的缓存不存在时，尝试通过API获取数据
        if (!redisService.hasKey(WF_VOIDTRADER_KEY)) {
            val traderJson =
                try {
                    httpUtil.doGetJson(WARFRAME_STATUS_VOID_TRADER, proxy = proxyUtil.randomProxy())
                } catch (e: Exception) {
                    // 如果使用代理的请求失败，则尝试使用无代理方式
                    httpUtil.doGetJson(WARFRAME_STATUS_VOID_TRADER)
                }

            val startString = traderJson["startString"].asText().replaceTime()
            val endString = traderJson["endString"].asText().replaceTime()
            val location = traderJson["location"].asText().turnZhHans()

            if (traderJson["inventory"].isEmpty) {
                // 向Redis中写入缓存，用于提示
                redisService.setValueWithExpiry(
                    WF_VOID_TRADER_COME_KEY,
                    location,
                    startString.parseDuration(),
                    TimeUnit.SECONDS
                )
                return null
            } else {
                // 定义一个正则表达式用于匹配中文字符
                val chinesePattern = Pattern.compile("[\\u4e00-\\u9fff]+")

                val itemList = traderJson["inventory"].map { item ->
                    val regex = Regex("000$")
                    WfStatusVo.VoidTraderItem(
                        item = wfLexiconService.getZhName(item["item"].asText()) ?: item["item"].asText(),
                        ducats = item["ducats"].asInt(),
                        credits = regex.replace(item["credits"].asText(), "k")
                    )
                }

                // 使用sortedWith根据item是否包含中文进行排序
                val sortedItemList = itemList.sortedWith(compareByDescending {
                    it.item?.let { it1 -> chinesePattern.matcher(it1).find() }
                })
                val voidTraderEntity = WfStatusVo.VoidTraderEntity(
                    time = endString,
                    location = location,
                    items = sortedItemList
                )

                redisService.setValueWithExpiry(
                    WF_VOIDTRADER_KEY,
                    voidTraderEntity,
                    endString.parseDuration(),
                    TimeUnit.SECONDS
                )

                return voidTraderEntity
            }
        } else {
            // 从Redis获取缓存数据
            return redisService.getValueTyped<WfStatusVo.VoidTraderEntity>(WF_VOIDTRADER_KEY)
        }
    }

    @Scheduled(cron = "10 0 8 * * 1")
    fun getSteelPathData(): WfStatusVo.SteelPathEntity? {
        if (!redisService.hasKey(WF_STEELPATH_KEY)) {

            val steelPath = HttpUtil.doGetJson(
                WARFRAME_STATUS_STEEL_PATH,
                params = mapOf("language" to "zh"),
                proxy = proxyUtil.randomProxy()
            )

            val currentReward = steelPath["currentReward"]
            val currentName = currentReward["name"].asText()
            val currentCost = currentReward["cost"].asInt()

            // 寻找下一个奖励
            val rotation = steelPath["rotation"]
            val currentIndex = rotation.indexOfFirst { it["name"].asText() == currentName }
            val nextReward = if (currentIndex != -1 && currentIndex < rotation.size() - 1) {
                rotation[currentIndex + 1]
            } else rotation[0]

            // 获取下一个奖励
            val nextName = nextReward["name"].asText()
            val nextCost = nextReward["cost"].asInt()

            val remaining = steelPath["remaining"].asText().replaceTime()

            val steelPathEntity = WfStatusVo.SteelPathEntity(
                currentName = currentName,
                currentCost = currentCost,
                nextName = nextName,
                nextCost = nextCost,
                remaining = remaining
            )

            redisService.setValueWithExpiry(
                WF_STEELPATH_KEY,
                steelPathEntity,
                remaining.parseDuration(),
                TimeUnit.SECONDS
            )
            return steelPathEntity
        } else {
            return redisService.getValueTyped<WfStatusVo.SteelPathEntity>(WF_STEELPATH_KEY)
        }
    }

    @Scheduled(cron = "5 0 8 * * 1")
    fun getArchonHuntData(): WfStatusVo.ArchonHuntEntity? {
        if (!redisService.hasKey(WF_ARCHONHUNT_KEY)) {

            val archonHuntJson = HttpUtil.doGetJson(
                WARFRAME_STATUS_ARCHON_HUNT,
                params = mapOf("language" to "zh"),
                proxy = proxyUtil.randomProxy()
            )

            val bosses = arrayOf("欺谋狼主", "混沌蛇主", "诡文枭主")
            val rewards = arrayOf("深红源力石", "琥珀源力石", "蔚蓝源力石")

            val bossIndex = bosses.indexOf(archonHuntJson["boss"].asText().replaceFaction())
            val boss = if (bossIndex != -1) bosses[bossIndex] else "未知"
            val rewardItem = if (bossIndex != -1) rewards[bossIndex] else "未知"
            val nextBoss = if (bossIndex != -1) bosses[(bossIndex + 1) % bosses.size] else "未知"
            val nextRewardItem = if (bossIndex != -1) rewards[(bossIndex + 1) % rewards.size] else "未知"

            val taskList = archonHuntJson["missions"].map { item ->
                WfStatusVo.Missions(
                    node = item["node"].asText().turnZhHans(),
                    type = item["type"].asText().turnZhHans()
                )
            }

            val faction = archonHuntJson["factionKey"].asText().replaceFaction()
            val eta = archonHuntJson["eta"].asText().replaceTime()

            val archonHuntEntity = WfStatusVo.ArchonHuntEntity(
                faction = faction,
                boss = boss,
                eta = eta,
                taskList = taskList,
                rewardItem = rewardItem,
                nextBoss = nextBoss,
                nextRewardItem = nextRewardItem
            )

            redisService.setValueWithExpiry(
                WF_ARCHONHUNT_KEY,
                archonHuntEntity,
                eta.parseDuration(),
                TimeUnit.SECONDS
            )
            return archonHuntEntity
        } else {
            return redisService.getValueTyped<WfStatusVo.ArchonHuntEntity>(WF_ARCHONHUNT_KEY)
        }
    }


}