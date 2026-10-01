package bot.wuliang.utils

import bot.wuliang.config.WARFRAME_INCARNON
import bot.wuliang.config.WARFRAME_MARKET_ITEMS_ORDERS_V2
import bot.wuliang.config.WARFRAME_WEEKLY_RIVEN_PC
import bot.wuliang.config.WfMarketConfig.WF_ARCHONHUNT_KEY
import bot.wuliang.config.WfMarketConfig.WF_CALENDAR_KEY
import bot.wuliang.config.WfMarketConfig.WF_CONQUEST_KEY
import bot.wuliang.config.WfMarketConfig.WF_FISSURE_KEY
import bot.wuliang.config.WfMarketConfig.WF_INCARNON_KEY
import bot.wuliang.config.WfMarketConfig.WF_INVASIONS_KEY
import bot.wuliang.config.WfMarketConfig.WF_NIGHTWAVE_KEY
import bot.wuliang.config.WfMarketConfig.WF_RIVEN_REROLLED_KEY
import bot.wuliang.config.WfMarketConfig.WF_RIVEN_UN_REROLLED_KEY
import bot.wuliang.config.WfMarketConfig.WF_SIMARIS_KEY
import bot.wuliang.config.WfMarketConfig.WF_SORTIE_KEY
import bot.wuliang.config.WfMarketConfig.WF_STEELPATH_KEY
import bot.wuliang.config.WfMarketConfig.WF_VOIDTRADER_KEY
import bot.wuliang.httpUtil.HttpUtil
import bot.wuliang.jacksonUtil.JacksonUtil
import bot.wuliang.moudles.*
import bot.wuliang.redis.RedisService
import bot.wuliang.translation.PublicExportService
import bot.wuliang.translation.WorldStateCatalog
import bot.wuliang.utils.TimeUtils.formatDuration
import bot.wuliang.utils.TimeUtils.getFirstDayOfWeek
import bot.wuliang.utils.TimeUtils.getInstantNow
import bot.wuliang.utils.TimeUtils.getLastDayOfWeek
import bot.wuliang.utils.TimeUtils.getNextMonday
import bot.wuliang.utils.TimeUtils.getStartOfDay
import bot.wuliang.utils.TimeUtils.getTimeOfNextDay
import bot.wuliang.utils.TimeUtils.parseDuration
import bot.wuliang.utils.TimeUtils.toNow
import com.fasterxml.jackson.databind.JsonNode
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.io.File
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import kotlin.math.abs

/** 解析实时世界状态，译文通过统一服务取得，包含译文的结果使用带翻译版本的缓存键。 */
@Component
class ParseDataUtil {
    @Autowired
    private lateinit var redisService: RedisService

    @Autowired
    private lateinit var translations: PublicExportService

    @Autowired
    private lateinit var worldStateCatalog: WorldStateCatalog

    @Autowired
    private lateinit var wfUtil: WfUtil

    private val incarnonWeekSeconds = 604800L

    /**
     * 通用解析常规突击任务（每日突击与周突击）
     * @param sortiesJson 突击任务数据
     * @param cacheKey 已附加翻译版本的业务缓存键
     * @param missionKey 突击任务数据中的任务列表key
     */
    private fun parseCommonSortie(
        sortiesJson: JsonNode,
        cacheKey: String,
        missionKey: String
    ): Sortie? {
        val now = getInstantNow()
        // 跨周时上游可能同时返回前后两期；必须按有效期选择，不能依赖数组顺序。
        val sortie = sortiesJson.filter { isActive(it, now) }
            .maxByOrNull { parseTimestamp(it["Activation"])!! }
        if (sortie == null) {
            redisService.deleteKey(cacheKey)
            return null
        }
        val bossCode = sortie["Boss"]?.asText().orEmpty()
        val boss = worldStateCatalog.boss(bossCode)
        val currentIndex = WorldStateCatalog.archons.indexOf(bossCode)

        val (rewardItem, nextBoss, nextRewardItem) = when {
            currentIndex >= 0 -> {
                val nextIndex = (currentIndex + 1) % WorldStateCatalog.archons.size
                Triple(
                    translations.name(WorldStateCatalog.archonShards[currentIndex]),
                    worldStateCatalog.boss(WorldStateCatalog.archons[nextIndex]).name,
                    translations.name(WorldStateCatalog.archonShards[nextIndex])
                )
            }

            else -> Triple(null, null, null)
        }


        val sortieEntity = Sortie(
            id = sortie["_id"]["\$oid"].asText(),
            activation = parseTimestamp(sortie["Activation"]),
            expiry = parseTimestamp(sortie["Expiry"]),
            boss = boss.name,
            rewardItem = rewardItem,
            nextBoss = nextBoss,
            nextRewardItem = nextRewardItem,
            faction = boss.faction,
            eta = formatDuration(Duration.between(getInstantNow(), parseTimestamp(sortie["Expiry"]))),
            variants = sortie[missionKey].map { variant ->
                Variants(
                    missionType = variant["missionType"]?.asText()?.let(translations::name),
                    modifierType = variant["modifierType"]?.asText()?.let(translations::name),
                    node = translations.node(variant["node"]?.asText().orEmpty()).name
                )
            },
        )

        redisService.setValueWithExpiry(
            cacheKey,
            sortieEntity,
            Duration.between(now, sortieEntity.expiry).seconds.coerceAtLeast(1),
            TimeUnit.SECONDS
        )

        return sortieEntity
    }

    /**
     * 解析每日突击任务
     */
    fun parseSorties(sortiesJson: JsonNode): Sortie? {
        return parseCommonSortie(sortiesJson, translations.cacheKey(WF_SORTIE_KEY), "Variants")
    }

    /**
     * 解析周突击任务
     */
    fun parseArchonHunt(sortiesJson: JsonNode): Sortie? {
        return parseCommonSortie(sortiesJson, translations.cacheKey(WF_ARCHONHUNT_KEY), "Missions")
    }

    /**
     * 解析钢铁之路
     */
    fun parseSteelPath(): Pair<Long?, SteelPath?> {
        translations.index()
        val cacheKey = translations.cacheKey(WF_STEELPATH_KEY)
        if (redisService.hasKey(cacheKey)) return redisService.getExpireAndValueTyped<SteelPath>(
            cacheKey
        )
        val rotation = worldStateCatalog.steelRewards
        val start = Instant.parse("2020-11-16T00:00:00.000Z")
        val sSinceStart = Duration.between(start, Instant.now()).seconds
        val eightWeeks = 4838400
        val sevenDays = 604800

        val ind = ((sSinceStart % eightWeeks) / sevenDays).toInt()

        // 计算下周的索引
        val nextInd = (ind + 1) % 8 // 共有8个周期

        val activation = getFirstDayOfWeek()
        val expiry = getLastDayOfWeek()

        val steelPathEntity = SteelPath(
            id = "spi:${getStartOfDay().toEpochMilli()}",
            activation = activation,
            expiry = expiry,
            eta = formatDuration(Duration.between(getInstantNow(), expiry)),
            currentItem = worldStateCatalog.steelName(rotation[ind]),
            currentCost = rotation[ind].cost,
            nextItem = worldStateCatalog.steelName(rotation[nextInd]),
            nextCost = rotation[nextInd].cost
        )
        redisService.setValueWithExpiry(
            cacheKey,
            steelPathEntity,
            steelPathEntity.eta?.parseDuration() ?: 0L,
            TimeUnit.SECONDS
        )
        return Pair(steelPathEntity.eta?.parseDuration(), steelPathEntity)
    }

    private fun parseTimestamp(dateNode: JsonNode?): Instant? {
        return dateNode?.get("\$date")?.get("\$numberLong")?.asText()
            ?.toLongOrNull()
            ?.let { Instant.ofEpochMilli(it) }
    }

    /** 生效时间判断 */
    private fun isActive(node: JsonNode, now: Instant): Boolean {
        val activation = parseTimestamp(node["Activation"]) ?: return false
        val expiry = parseTimestamp(node["Expiry"]) ?: return false
        return !activation.isAfter(now) && expiry.isAfter(now)
    }

    /** 成功获取的周任务缓存至下一次轮换；当前任务尚未发布时才使用短缓存。 */
    private fun rotationCacheSeconds(nodes: Iterable<JsonNode>, now: Instant): Long {
        if (nodes.none { isActive(it, now) }) return 30
        return nodes
            .flatMap { listOfNotNull(parseTimestamp(it["Activation"]), parseTimestamp(it["Expiry"])) }
            .filter { it.isAfter(now) }
            .minOfOrNull { Duration.between(now, it).seconds.coerceAtLeast(1) } ?: 30
    }

    /**
     * 解析午夜电波挑战任务
     *
     * @param challengesNode 午夜电波挑战任务数据
     */
    private fun parseChallenges(challengesNode: Iterable<JsonNode>?): List<Challenges> {
        return challengesNode?.map { challenge ->
            val challengeText = challenge["Challenge"]?.asText() ?: ""
            val isDaily = challenge["Daily"]?.asBoolean() ?: false
            val isElite = challengeText.lowercase().contains("hard")

            Challenges(
                id = challenge["_id"]?.get("\$oid")?.asText() ?: "",
                isDaily = isDaily,
                isElite = isElite,
                isPermanent = challenge["Permanent"]?.asBoolean() ?: false,
                title = translations.name(challengeText),
                desc = translations.description(challengeText) ?: challengeText,
                reputation = when {
                    isDaily -> 1000
                    isElite -> 7000
                    else -> 4500
                }
            )
        } ?: emptyList()
    }

    /**
     * 解析午夜电波
     * @param nightWaveJson 午夜电波Json
     */
    fun parseNightWave(nightWaveJson: JsonNode): NightWave? {
        translations.index()
        val cacheKey = translations.cacheKey(WF_NIGHTWAVE_KEY)
        val expiryTime = parseTimestamp(nightWaveJson["Expiry"])
        val activation = parseTimestamp(nightWaveJson["Activation"])
        val now = getInstantNow()
        val challenges = nightWaveJson["ActiveChallenges"]?.filter { isActive(it, now) }.orEmpty()
        val nightWaveEntity = NightWave(
            id = "nightwave${parseTimestamp(nightWaveJson["Expiry"])}",
            activation = activation,
            expiry = expiryTime,
            eta = formatDuration(Duration.between(now, expiryTime)),
            startTime = formatDuration(Duration.between(now, activation)),
            tag = nightWaveJson["AffiliationTag"].asText(),
            season = nightWaveJson["Season"].asInt(),
            phase = nightWaveJson["Phase"].asInt(),
            params = nightWaveJson["Params"].asText(),
            possibleChallenges = nightWaveJson["Challenges"]?.let { parseChallenges(it) },
            activeChallenges = parseChallenges(challenges)
        )

        // 周常缓存以每周挑战的轮换时间为准，不因每日挑战提前失效。
        val weeklyChallenges = nightWaveJson["ActiveChallenges"]
            ?.filter { it["Daily"]?.asBoolean() != true }.orEmpty()
        val expire = rotationCacheSeconds(weeklyChallenges, now)
        redisService.setValueWithExpiry(cacheKey, nightWaveEntity, expire, TimeUnit.SECONDS)
        return nightWaveEntity
    }


    /**
     * 解析裂缝
     * @param fissureJson 裂缝数据
     * @param isStorm 是否是九重天裂缝
     */
    fun parseFissureArray(fissureJson: JsonNode, isStorm: Boolean = false): List<Fissure> {
        val fissureEntity = fissureJson.map { fissure ->
            val node = translations.node(fissure["Node"]?.asText().orEmpty())
            val expiry = parseTimestamp(fissure["Expiry"])
            val modifierNum =
                worldStateCatalog.tier(fissure[if (isStorm) "ActiveMissionTier" else "Modifier"]?.asText().orEmpty())
            Fissure(
                id = fissure["_id"]?.get("\$oid")?.asText() ?: "",
                activation = parseTimestamp(fissure["Activation"]),
                expiry = expiry,
                eta = formatDuration(Duration.between(getInstantNow(), expiry)).replace("\\s+".toRegex(), ""),
                node = node.name,
                missionType = fissure["MissionType"]?.asText()?.let(translations::name)
                    ?: node.type
                    ?: translations.name("MT_DEFAULT"),
                faction = node.faction,
                modifier = modifierNum.value,
                modifierValue = modifierNum.num,
                hard = fissure["Hard"]?.asBoolean() ?: false,
                storm = isStorm
            )
        }
        return fissureEntity
    }

    /**
     * 解析裂缝
     * @param fissureJson 裂缝数据
     * @param stormFissureJson 九重天裂缝数据
     */
    suspend fun parseFissure(fissureJson: JsonNode, stormFissureJson: JsonNode): List<Fissure?>? {
        translations.index()
        val cacheKey = translations.cacheKey(WF_FISSURE_KEY)
        if (redisService.hasKey(cacheKey)) return redisService.getValueTyped<List<Fissure?>>(cacheKey)

        return coroutineScope {
            val fissureJob = async { parseFissureArray(fissureJson) }
            val stormFissureJob = async { parseFissureArray(stormFissureJson, true) }
            val fissureList = fissureJob.await() + stormFissureJob.await()
            val filteredFissureList = fissureList.filter {
                it.eta?.parseDuration() != null && it.eta!!.parseDuration() >= 0
            }.sortedBy { it.modifierValue }
            val expire = filteredFissureList
                .minOfOrNull { it.eta?.parseDuration() ?: Long.MAX_VALUE }
                ?.coerceAtMost(300)
                ?.coerceAtLeast(30) ?: 300
            redisService.setValueWithExpiry(cacheKey, filteredFissureList, expire, TimeUnit.SECONDS)
            filteredFissureList
        }
    }

    /**
     * 解析虚空交易
     * @param voidTradersJsonNode 虚空商人Json数据Node
     */
    fun parseVoidTraders(voidTradersJsonNode: JsonNode): List<VoidTrader>? {
        translations.index()
        val cacheKey = translations.cacheKey(WF_VOIDTRADER_KEY)
        if (redisService.hasKey(cacheKey)) return redisService.getValueTyped<List<VoidTrader>>(
            cacheKey
        )
        val voidTradersList = voidTradersJsonNode.map { voidTrader ->
            val activationTime = parseTimestamp(voidTrader["Activation"])
            val isActive = activationTime?.let {
                getInstantNow().isAfter(it)
            } ?: false

            val inventory = voidTrader["Manifest"]?.let { manifest ->
                val items = manifest.map { voidTraderItem ->
                    val voidItem = voidTraderItem["ItemType"]?.asText() ?: ""
                    VoidTraderItem(
                        item = translations.name(voidItem),
                        ducats = voidTraderItem["PrimePrice"].asInt(),
                        credits = voidTraderItem["RegularPrice"].asInt(),
                    )
                }
                    .filter { item -> !item.item.isNullOrBlank() }

                // 基于最长公共子串的前缀/后缀识别算法
                fun longestCommonPrefix(s1: String, s2: String): String {
                    val minLength = minOf(s1.length, s2.length)
                    for (i in 0 until minLength) {
                        if (s1[i] != s2[i]) {
                            return s1.substring(0, i)
                        }
                    }
                    return s1.substring(0, minLength)
                }

                fun longestCommonSuffix(s1: String, s2: String): String {
                    val minLength = minOf(s1.length, s2.length)
                    for (i in 1..minLength) {
                        if (s1[s1.length - i] != s2[s2.length - i]) {
                            return s1.substring(s1.length - i + 1)
                        }
                    }
                    return s1.substring(s1.length - minLength)
                }

                // 计算所有物品之间的公共前缀和后缀
                val prefixCount = mutableMapOf<String, Int>()
                val suffixCount = mutableMapOf<String, Int>()

                // 两两比较计算公共前缀和后缀
                for (i in items.indices) {
                    for (j in i + 1 until items.size) {
                        val item1 = items[i].item ?: continue
                        val item2 = items[j].item ?: continue

                        val commonPrefix = longestCommonPrefix(item1.lowercase(), item2.lowercase())
                        val commonSuffix = longestCommonSuffix(item1.lowercase(), item2.lowercase())

                        // 只考虑有一定长度的公共前缀/后缀（至少2个字符）
                        if (commonPrefix.length >= 2) {
                            prefixCount[commonPrefix] = prefixCount.getOrDefault(commonPrefix, 0) + 1
                        }

                        if (commonSuffix.length >= 2) {
                            suffixCount[commonSuffix] = suffixCount.getOrDefault(commonSuffix, 0) + 1
                        }
                    }
                }

                // 确定常见前缀和后缀（出现次数>=2）
                val commonPrefixes = prefixCount.filter { it.value >= 2 }.keys
                val commonSuffixes = suffixCount.filter { it.value >= 2 }.keys

                // 找到物品的最佳匹配前缀/后缀
                fun findBestPrefix(itemName: String): String {
                    val lowerItemName = itemName.lowercase()
                    return commonPrefixes
                        .filter { lowerItemName.startsWith(it) }
                        .maxByOrNull { it.length } ?: ""
                }

                fun findBestSuffix(itemName: String): String {
                    val lowerItemName = itemName.lowercase()
                    return commonSuffixes
                        .filter { lowerItemName.endsWith(it) }
                        .maxByOrNull { it.length } ?: ""
                }

                // 排序逻辑
                items.sortedWith(compareBy<VoidTraderItem> { item ->
                    // 首先将有分组的物品排在前面
                    val hasCommonPrefix = findBestPrefix(item.item ?: "").isNotEmpty()
                    val hasCommonSuffix = findBestSuffix(item.item ?: "").isNotEmpty()

                    // 没有分组的物品排在后面（返回1），有分组的排在前面（返回0）
                    if (hasCommonPrefix || hasCommonSuffix) 0 else 1
                }.thenBy { item ->
                    // 然后按前缀分组
                    findBestPrefix(item.item ?: "")
                }.thenBy { item ->
                    // 再按后缀分组
                    findBestSuffix(item.item ?: "")
                }.thenBy { item ->
                    // 再按基础名称排序（去除常见前缀和后缀）
                    val itemName = item.item ?: ""
                    val lowerItemName = itemName.lowercase()
                    val bestPrefix = findBestPrefix(itemName)
                    val bestSuffix = findBestSuffix(itemName)

                    var result = lowerItemName
                    if (bestPrefix.isNotEmpty()) {
                        result = result.substring(bestPrefix.length).trim()
                    }
                    if (bestSuffix.isNotEmpty()) {
                        if (result.endsWith(bestSuffix)) {
                            result = result.substring(0, result.length - bestSuffix.length).trim()
                        }
                    }
                    result
                }.thenByDescending { item ->
                    // 优先显示包含中文的物品
                    item.item?.let { Pattern.compile("[\\u4e00-\\u9fff]+").matcher(it).find() } ?: false
                }.thenBy { item ->
                    // 最后按完整名称排序
                    item.item?.lowercase() ?: ""
                })
            }


            val now = getInstantNow()

            VoidTrader(
                id = voidTrader["_id"]?.get("\$oid")?.asText() ?: "",
                activation = parseTimestamp(voidTrader["Activation"]),
                expiry = parseTimestamp(voidTrader["Expiry"]),
                eta = if (isActive) {
                    formatDuration(Duration.between(now, parseTimestamp(voidTrader["Expiry"])))
                } else {
                    formatDuration(Duration.between(now, parseTimestamp(voidTrader["Activation"])))
                },
                isActive = isActive,
                node = translations.node(voidTrader["Node"]?.asText().orEmpty()).name,
                inventory = inventory
            )
        }

        val expire = voidTradersList
            .minOfOrNull { it.eta?.parseDuration() ?: Long.MAX_VALUE }
            ?.coerceAtLeast(30) ?: 300
        redisService.setValueWithExpiry(cacheKey, voidTradersList, expire, TimeUnit.SECONDS)
        return voidTradersList
    }

    /**
     * 解析圣殿结合仪式目标信息
     * @param simarisJson 圣殿结合仪式目标Json
     */
    fun parseSimaris(simarisJson: JsonNode): Simaris? {
        translations.index()
        val cacheKey = translations.cacheKey(WF_SIMARIS_KEY)
        if (redisService.hasKey(cacheKey)) return redisService.getValueTyped<Simaris>(cacheKey)
        val targetItem = simarisJson["LastCompletedTargetType"].textValue()
        val simarisPersistent = worldStateCatalog.synthesisTarget(targetItem)
            ?: SimarisPersistent(imageKey = null, name = translations.name(targetItem), locations = emptyList())
        val today = getStartOfDay()
        val nextDay = getTimeOfNextDay(today)
        val simaris = Simaris(
            imageKey = simarisPersistent.imageKey,
            name = simarisPersistent.name,
            activation = today,
            expiry = nextDay,
            eta = formatDuration(Duration.between(getInstantNow(), nextDay)),
            locations = simarisPersistent.locations,
        )
        val expire = simaris.eta?.parseDuration() ?: 300
        redisService.setValueWithExpiry(cacheKey, simaris, expire, TimeUnit.SECONDS)
        return simaris
    }

    /**
     * 解析入侵信息
     * @param invasionsJson 入侵信息Json
     */
    fun parseInvasions(invasionsJson: JsonNode): List<Invasions>? {
        translations.index()
        val cacheKey = translations.cacheKey(WF_INVASIONS_KEY)
        if (redisService.hasKey(cacheKey)) return redisService.getValueTyped<List<Invasions>>(cacheKey)
        val invasionsList = invasionsJson.map { invasions ->
            val count = invasions["Count"].intValue()
            val activation = parseTimestamp(invasions["Activation"])
            val completedRuns = abs(count)
            val elapsedMillis = activation?.let { abs(toNow(it)) }
            val requiredRuns = invasions["Goal"].intValue()
            val remainingRuns = requiredRuns.minus(completedRuns)
            val remainingTime = if (completedRuns > 0) {
                remainingRuns.times((elapsedMillis?.div(completedRuns) ?: 0))
            } else {
                // 当还没有完成任何运行时，无法估算剩余时间
                -9999L
            }
            val faction = invasions["Faction"].textValue()
            val vsInfestation = faction == "FC_INFESTATION"

            Invasions(
                id = invasions["_id"].get("\$oid")?.asText(),
                activation = activation,
                eta = if (remainingTime != -9999L) remainingTime.let { Duration.ofMillis(it) }
                    ?.let { formatDuration(it) } else "无法估算",
                desc = translations.name(invasions["LocTag"].asText()),
                faction = worldStateCatalog.faction(faction),
                defenderFaction = worldStateCatalog.faction(invasions["DefenderFaction"].textValue()),
                node = translations.node(invasions["Node"]?.asText().orEmpty()).name,
                count = count,
                requiredRuns = requiredRuns,
                completion = if (vsInfestation) (1 + count.toDouble() / requiredRuns.toDouble()) * 100 else (1 + count.toDouble() / requiredRuns.toDouble()) * 50,
                completed = invasions["Completed"].booleanValue(),
                vsInfestation = vsInfestation,
                attackerReward = if (invasions["AttackerReward"].has("countedItems")) invasions["AttackerReward"]["countedItems"].map { item ->
                    Modifiers(
                        translations.name(item["ItemType"].asText()),
                        item["ItemCount"].intValue()
                    )
                } else null,
                defenderReward = invasions["DefenderReward"]["countedItems"].map { item ->
                    Modifiers(
                        translations.name(item["ItemType"].asText()),
                        item["ItemCount"].intValue()
                    )
                },
            )
        }
        val completedInvasions = invasionsList.filter { it.completed == false }

        val expire = completedInvasions
            .minOfOrNull { it.eta?.parseDuration() ?: Long.MAX_VALUE }
            ?.coerceAtMost(10)
            ?.coerceAtLeast(5) ?: 10
        redisService.setValueWithExpiry(cacheKey, completedInvasions, expire, TimeUnit.MINUTES)
        return completedInvasions
    }

    /**
     * 解析DE紫卡周榜信息
     */
    fun parseWeeklyRiven() {
        val unRerolledKey = translations.cacheKey(WF_RIVEN_UN_REROLLED_KEY)
        val rerolledKey = translations.cacheKey(WF_RIVEN_REROLLED_KEY)
        if (redisService.hasKey(unRerolledKey) && redisService.hasKey(rerolledKey)) return
        val data = HttpUtil.doGetStr(WARFRAME_WEEKLY_RIVEN_PC)
        val jsonData = JacksonUtil.readTree(JacksonUtil.convertSingleJsObjectToStandardJson(data))
        val rawRivenList = jsonData.map { eachRiven ->
            Riven(
                itemType = eachRiven["itemType"].textValue(),
                compatibility = eachRiven["compatibility"].textValue(),
                rerolled = eachRiven["rerolled"].booleanValue(),
                avg = eachRiven["avg"].doubleValue(),
                stddev = eachRiven["stddev"].doubleValue(),
                min = eachRiven["min"].intValue(),
                max = eachRiven["max"].intValue(),
                pop = eachRiven["pop"].intValue(),
                median = eachRiven["median"].doubleValue()
            )
        }

        val rivenList = rawRivenList.map { riven ->
            riven.copy(
                compatibilityId = riven.compatibility,
                compatibility = (riven.compatibility ?: riven.itemType)?.let(translations::name)
            )
        }
        // 根据rerolled字段将列表分成两个列表
        val rerolledList = rivenList.filter { it.rerolled == true }
        val unRerolledList = rivenList.filter { it.rerolled == false }

        val expire = Duration.between(Instant.now(), getNextMonday()).seconds

        redisService.setValueWithExpiry(unRerolledKey, unRerolledList, expire, TimeUnit.SECONDS)
        redisService.setValueWithExpiry(rerolledKey, rerolledList, expire, TimeUnit.SECONDS)
    }

    /**
     * 解析回廊相关信息
     */
    fun parseIncarnon(): Incarnon? {
        translations.index()
        val cacheKey = translations.cacheKey(WF_INCARNON_KEY)
        if (redisService.hasKey(cacheKey)) return redisService.getValueTyped<Incarnon>(cacheKey)
        val expire = Duration.between(Instant.now(), getNextMonday()).seconds

        val incarnonJson = JacksonUtil.readTree(File(WARFRAME_INCARNON))
        val ordinaryJson = incarnonJson["ordinary"]
        val steelJson = incarnonJson["steel"]
        val ordinaryScheduleJson = incarnonJson["ordinarySchedule"]
        val steelScheduleJson = incarnonJson["steelSchedule"]

        // 获取到现在经过的秒数
        val now = Instant.now()
        val ordinaryInd = calculateIncarnonIndex(ordinaryScheduleJson, ordinaryJson.size(), now)
        val steelInd = calculateIncarnonIndex(steelScheduleJson, steelJson.size(), now)

        // 计算下周的索引
        val activation = getFirstDayOfWeek()
        val expiry = getNextMonday()
        val ordinaryNextInd = calculateIncarnonIndex(ordinaryScheduleJson, ordinaryJson.size(), expiry)
        val steelNextInd = calculateIncarnonIndex(steelScheduleJson, steelJson.size(), expiry)

        // 确保执行灵化解析时已存在紫卡价格数据
        parseWeeklyRiven()
        val rivenPriceList = redisService.getValueTyped<List<Riven>>(translations.cacheKey(WF_RIVEN_UN_REROLLED_KEY))

        fun processSteelItems(steelJsonNode: JsonNode): List<Incarnon.SteelItem> {
            return steelJsonNode["items"].map { item ->
                val urlName = item["url_name"].textValue()
                val adapter = translations.index().incarnonAdapter(urlName)
                Incarnon.SteelItem(
                    name = translations.name(adapter ?: urlName),
                    riven = item["riven"].doubleValue(),
                    urlName = urlName,
                    rivenPrice = rivenPriceList?.find {
                        it.compatibilityId.equals(urlName.replace('_', ' '), ignoreCase = true)
                    }?.median ?: 0.0,
                )
            }.toList()
        }

        val incarnon = Incarnon(
            thisWeek = Incarnon.IncarnonData(
                ordinary = Incarnon.WeekData(
                    week = ordinaryInd + 1,
                    items = ordinaryJson[ordinaryInd]["items"].map { translations.name(it.textValue()) },
                ),
                steel = Incarnon.WeekData(
                    week = steelInd + 1,
                    items = processSteelItems(steelJson[steelInd]),
                )
            ),
            nextWeek = Incarnon.IncarnonData(
                ordinary = Incarnon.WeekData(
                    week = ordinaryNextInd + 1,
                    items = ordinaryJson[ordinaryNextInd]["items"].map { translations.name(it.textValue()) },
                ),
                steel = Incarnon.WeekData(
                    week = steelNextInd + 1,
                    items = processSteelItems(steelJson[steelNextInd])
                )
            ),
            activation = activation,
            expiry = expiry,
            eta = formatDuration(Duration.between(getInstantNow(), expiry))
        )

        redisService.setValueWithExpiry(cacheKey, incarnon, expire, TimeUnit.SECONDS)
        return incarnon
    }

    private fun calculateIncarnonIndex(scheduleJson: JsonNode, itemSize: Int, now: Instant): Int {
        val schedule = scheduleJson
            .filter { !Instant.parse(it["startTime"].textValue()).isAfter(now) }
            .maxByOrNull { Instant.parse(it["startTime"].textValue()) }
            ?: scheduleJson.first()

        val startTime = Instant.parse(schedule["startTime"].textValue())
        val startIndex = schedule["startIndex"].longValue()
        val cycleSize = schedule["size"].longValue()
        require(cycleSize in 1..itemSize.toLong()) { "无效的灵化索引大小: $cycleSize" }
        require(startIndex in 0 until cycleSize) { "无效的灵化开始索引: $startIndex" }

        val elapsedWeeks = Math.floorDiv(Duration.between(startTime, now).seconds, incarnonWeekSeconds)
        return Math.floorMod(startIndex + elapsedWeeks, cycleSize).toInt()
    }

    fun parseWmMinimalPrice(key: String): Int {
        val headers: MutableMap<String, Any> = mutableMapOf(
            "accept" to "application/json",
            "language" to "en",
            "platform" to "pc",
            "crossplay" to "false"
        )
        val topJson = HttpUtil.doGetJson(
            url = "$WARFRAME_MARKET_ITEMS_ORDERS_V2/$key/top",
            headers = headers
        )
        val topOrders = topJson["data"]["sell"]
            ?.takeIf { it.isArray }
            ?.toList()
            ?: emptyList()
        if (topOrders.isNotEmpty()) {
            return topOrders.first()["platinum"].intValue()
        }

        val json = HttpUtil.doGetJson(
            url = "$WARFRAME_MARKET_ITEMS_ORDERS_V2/$key",
            headers = headers
        )
        val fallbackData = json["data"]
        val fallbackNode = when {
            fallbackData.isArray -> fallbackData
            fallbackData["sell"]?.isArray == true -> fallbackData["sell"]
            else -> null
        }
        val fallbackOrders = fallbackNode
            ?.filter {
                it["type"].textValue() == "sell" &&
                        it["visible"].asBoolean(false)
            }
            ?: emptyList()
        if (fallbackOrders.isEmpty()) {
            return 0
        }

        val minimalOrder = fallbackOrders.minByOrNull { it["platinum"].intValue() } ?: return 0
        val price = minimalOrder["platinum"].intValue()
        return price
    }


    /**
     * 解析科研任务（深层科研 / 时光科研），仅读取 CD_HARD 难度的数据。
     * 偏差、风险和额外变量使用专项词库；CT_LAB、CT_HEX 保留为前端展示用的协议代码。
     * @param conquestsJson Conquests 数组节点
     */
    fun parseConquestArray(conquestsJson: JsonNode): List<Conquest>? {
        translations.index()
        val cacheKey = translations.cacheKey(WF_CONQUEST_KEY)
        val now = getInstantNow()
        val conquestEntity = conquestsJson.filter { isActive(it, now) }.map { conquest ->
            val expiry = parseTimestamp(conquest["Expiry"])
            val type = conquest["Type"]?.asText()

            val missions = conquest["Missions"]?.mapNotNull { mission ->
                val missionTypeKey = mission["missionType"]?.asText()
                val factionKey = mission["faction"]?.asText()

                // 只取 CD_HARD 难度
                val hardDifficulty = mission["difficulties"]?.find { it["type"]?.asText() == "CD_HARD" }
                val difficulty = hardDifficulty?.let { diff ->
                    val deviationKey = diff["deviation"]?.asText()
                    val deviationInfo = deviationKey?.let {
                        translations.conquestInfo(it)
                    }
                    val risks = diff["risks"]?.mapNotNull { riskNode ->
                        val riskKey = riskNode?.asText()
                        riskKey?.let {
                            translations.conquestInfo(it)
                        }
                    } ?: emptyList()
                    ConquestDifficulty(deviation = deviationInfo, risks = risks)
                }

                ConquestMission(
                    faction = factionKey?.let {
                        worldStateCatalog.faction(it)
                    },
                    missionType = missionTypeKey?.let {
                        translations.name(it)
                    },
                    difficulty = difficulty
                )
            }?.toList() ?: emptyList()

            val variables = conquest["Variables"]?.mapNotNull { varNode ->
                val varKey = varNode?.asText()
                varKey?.let {
                    translations.conquestInfo(it)
                }
            } ?: emptyList()

            Conquest(
                activation = parseTimestamp(conquest["Activation"]),
                expiry = expiry,
                eta = expiry?.let {
                    formatDuration(Duration.between(getInstantNow(), it)).replace("\\s+".toRegex(), "")
                },
                type = type,
                missions = missions,
                variables = variables
            )
        }

        val expire = rotationCacheSeconds(conquestsJson, now)
        redisService.setValueWithExpiry(cacheKey, conquestEntity, expire, TimeUnit.SECONDS)
        return conquestEntity
    }

    /**
     * 解析 1999 日历：挑战与奖励走统一翻译，增幅使用独立本地词库。
     * 季节和 CET 事件类型保留协议代码，具体标签由前端固定显示。
     * @param calendarJson KnownCalendarSeasons 数组节点
     */
    fun parseCalendarArray(calendarJson: JsonNode): CalendarSeason? {
        translations.index()
        val cacheKey = translations.cacheKey(WF_CALENDAR_KEY)
        if (redisService.hasKey(cacheKey)) return redisService.getValueTyped<CalendarSeason>(cacheKey)

        val seasonNode = calendarJson[0] ?: return null
        val expiry = parseTimestamp(seasonNode["Expiry"])
        val seasonKey = seasonNode["Season"]?.asText()

        val days = seasonNode["Days"]?.mapNotNull { dayNode ->
            val day = dayNode["day"]?.asInt() ?: return@mapNotNull null
            val events = dayNode["events"]
            val eventKey = events?.firstOrNull()?.let {
                when {
                    it.has("challenge") -> "CET_CHALLENGE"
                    it.has("reward") -> "CET_REWARD"
                    it.has("upgrade") -> "CET_UPGRADE"
                    else -> null
                }
            }

            // 如果是生日（events 为空且是生日日期）
            val birthday = WorldStateCatalog.birthdays[day]?.let(translations::name)

            if (birthday != null) {
                return@mapNotNull CalendarDay(
                    day = day,
                    date = wfUtil.dayOfYearToDate(day),
                    type = null,
                    typeKey = null,
                    items = emptyList(),
                    birthday = birthday
                )
            }

            val items = events?.mapNotNull { event ->
                val path = when {
                    event.has("challenge") -> event["challenge"]?.asText()
                    event.has("reward") -> event["reward"]?.asText()
                    event.has("upgrade") -> event["upgrade"]?.asText()
                    else -> null
                }
                path?.let {
                    if (event.has("upgrade")) translations.calendarUpgradeInfo(it) else translations.info(it)
                }
            }?.toList() ?: emptyList()

            CalendarDay(
                day = day,
                date = wfUtil.dayOfYearToDate(day),
                type = eventKey,
                typeKey = eventKey,
                items = items,
                birthday = null
            )
        }?.toList() ?: emptyList()

        val result = CalendarSeason(
            activation = parseTimestamp(seasonNode["Activation"]),
            expiry = expiry,
            eta = expiry?.let {
                formatDuration(Duration.between(getInstantNow(), it)).replace("\\s+".toRegex(), "")
            },
            season = seasonKey,
            seasonKey = seasonKey,
            days = days
        )

        val expire = result.eta?.parseDuration() ?: 300L
        redisService.setValueWithExpiry(cacheKey, result, expire, TimeUnit.SECONDS)
        return result
    }
}
