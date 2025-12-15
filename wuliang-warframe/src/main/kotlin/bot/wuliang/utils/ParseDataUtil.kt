package bot.wuliang.utils

import bot.wuliang.config.*
import bot.wuliang.config.WfMarketConfig.WF_ARCHONHUNT_KEY
import bot.wuliang.config.WfMarketConfig.WF_FISSURE_KEY
import bot.wuliang.config.WfMarketConfig.WF_INCARNON_KEY
import bot.wuliang.config.WfMarketConfig.WF_INVASIONS_KEY
import bot.wuliang.config.WfMarketConfig.WF_MARKET_CACHE_KEY
import bot.wuliang.config.WfMarketConfig.WF_MARKET_RIVEN_KEY
import bot.wuliang.config.WfMarketConfig.WF_NIGHTWAVE_KEY
import bot.wuliang.config.WfMarketConfig.WF_RIVEN_REROLLED_KEY
import bot.wuliang.config.WfMarketConfig.WF_RIVEN_UN_REROLLED_KEY
import bot.wuliang.config.WfMarketConfig.WF_SIMARIS_KEY
import bot.wuliang.config.WfMarketConfig.WF_SORTIE_KEY
import bot.wuliang.config.WfMarketConfig.WF_STEELPATH_KEY
import bot.wuliang.config.WfMarketConfig.WF_VOIDTRADER_KEY
import bot.wuliang.entity.WfRivenEntity
import bot.wuliang.httpUtil.HttpUtil
import bot.wuliang.jacksonUtil.JacksonUtil
import bot.wuliang.moudles.*
import bot.wuliang.redis.RedisService
import bot.wuliang.service.WfLexiconService
import bot.wuliang.service.WfRivenService
import bot.wuliang.utils.StringUtils.formatSpacesToUnderline
import bot.wuliang.utils.TimeUtils.formatDuration
import bot.wuliang.utils.TimeUtils.getInstantNow
import bot.wuliang.utils.TimeUtils.getLastDayOfWeek
import bot.wuliang.utils.TimeUtils.getStartOfDay
import bot.wuliang.utils.TimeUtils.getStartOfNextDay
import bot.wuliang.utils.TimeUtils.getTimeOfNextDay
import bot.wuliang.utils.TimeUtils.getFirstDayOfWeek
import bot.wuliang.utils.TimeUtils.getNextMonday
import bot.wuliang.utils.TimeUtils.parseDuration
import bot.wuliang.utils.TimeUtils.toNow
import bot.wuliang.utils.WfStatus.replaceFaction
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
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

@Component
class ParseDataUtil {
    @Autowired
    private lateinit var redisService: RedisService

    @Autowired
    private lateinit var wfLexiconService: WfLexiconService

    @Autowired
    private lateinit var wfRivenService: WfRivenService

    @Autowired
    private lateinit var wfUtil: WfUtil

    /**
     * 通用解析常规突击任务（每日突击与周突击）
     * @param sortiesJson 突击任务数据
     * @param cacheKey 缓存key
     * @param missionKey 突击任务数据中的任务列表key
     */
    private fun parseCommonSortie(
        sortiesJson: JsonNode,
        cacheKey: String,
        missionKey: String
    ): Sortie? {
        if (redisService.hasKey(cacheKey)) return redisService.getValueTyped<Sortie>(cacheKey)
        val keyPrefix = WF_MARKET_CACHE_KEY
        val sortie = sortiesJson.get(0)
        val boss = sortie["Boss"]?.asText()
            ?.let { redisService.getValueTyped<Boss>("${keyPrefix}Boss:${it}") }

        data class SortieReward(val boss: String, val reward: String)

        val sortieRewards = listOf(
            SortieReward("欺谋狼主", "深红源力石"),
            SortieReward("混沌蛇主", "琥珀源力石"),
            SortieReward("诡文枭主", "蔚蓝源力石")
        )

        val bossName = boss?.name
        val currentIndex = sortieRewards.indexOfFirst { it.boss == bossName }

        val (rewardItem, nextBoss, nextRewardItem) = when {
            currentIndex >= 0 -> {
                val nextIndex = (currentIndex + 1) % sortieRewards.size
                Triple(
                    sortieRewards[currentIndex].reward,
                    sortieRewards[nextIndex].boss,
                    sortieRewards[nextIndex].reward
                )
            }

            else -> Triple(null, null, null)
        }


        val sortieEntity = Sortie(
            id = sortie["_id"]["\$oid"].asText(),
            activation = parseTimestamp(sortie["Activation"]),
            expiry = parseTimestamp(sortie["Expiry"]),
            boss = boss?.name,
            rewardItem = rewardItem,
            nextBoss = nextBoss,
            nextRewardItem = nextRewardItem,
            faction = boss?.faction!!.replaceFaction(),
            eta = formatDuration(Duration.between(getInstantNow(), parseTimestamp(sortie["Expiry"]))),
            variants = sortie[missionKey].map { variant ->
                Variants(
                    missionType = redisService.getValueTyped<String>("${keyPrefix}MissionType:${variant["missionType"]?.asText()}"),
                    modifierType = redisService.getValueTyped<String>("${keyPrefix}ModifierType:${variant["modifierType"]?.asText()}"),
                    node = redisService.getValueTyped<Nodes>("${keyPrefix}Node:${variant["node"]?.asText()}")!!.name
                )
            },
        )

        redisService.setValueWithExpiry(
            cacheKey,
            sortieEntity,
            sortieEntity.eta?.parseDuration() ?: 0L,
            TimeUnit.SECONDS
        )

        return sortieEntity
    }

    /**
     * 解析每日突击任务
     */
    fun parseSorties(sortiesJson: JsonNode): Sortie? {
        return parseCommonSortie(sortiesJson, WF_SORTIE_KEY, "Variants")
    }

    /**
     * 解析周突击任务
     */
    fun parseArchonHunt(sortiesJson: JsonNode): Sortie? {
        return parseCommonSortie(sortiesJson, WF_ARCHONHUNT_KEY, "Missions")
    }

    /**
     * 解析钢铁之路
     */
    fun parseSteelPath(): Pair<Long?, SteelPath?> {
        if (redisService.hasKey(WF_STEELPATH_KEY)) return redisService.getExpireAndValueTyped<SteelPath>(
            WF_STEELPATH_KEY
        )
        val rotationJson = redisService.getValueTyped<ArrayNode>("${WF_MARKET_CACHE_KEY}SteelPath:Rotation")
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
            currentItem = rotationJson!!.get(ind)["name"].asText(),
            currentCost = rotationJson.get(ind)["cost"].asInt(),
            nextItem = rotationJson.get(nextInd)["name"].asText(),
            nextCost = rotationJson.get(nextInd)["cost"].asInt()
        )
        redisService.setValueWithExpiry(
            WF_STEELPATH_KEY,
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

    /**
     * 解析午夜电波挑战任务
     *
     * @param challengesNode 午夜电波挑战任务数据
     */
    private fun parseChallenges(challengesNode: JsonNode?): List<Challenges> {
        return challengesNode?.map { challenge ->
            val challengeText = challenge["Challenge"]?.asText() ?: ""
            val isDaily = challenge["Daily"]?.asBoolean() ?: false
            val isElite = challengeText.lowercase().contains("hard")

            Challenges(
                id = challenge["_id"]?.get("\$oid")?.asText() ?: "",
                isDaily = isDaily,
                isElite = isElite,
                isPermanent = challenge["Permanent"]?.asBoolean() ?: false,
                title = wfUtil.getLanguageValue(challengeText.lowercase()) ?: StringUtils.formatWithSpaces(
                    challengeText.split("/").lastOrNull() ?: ""
                ),
                desc = wfUtil.getLanguageDesc(challengeText.lowercase()) ?: StringUtils.formatWithSpaces(
                    challengeText.split("/").lastOrNull() ?: ""
                ),
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
        if (redisService.hasKey(WF_NIGHTWAVE_KEY)) return redisService.getValueTyped<NightWave>(WF_NIGHTWAVE_KEY)
        val expiryTime = parseTimestamp(nightWaveJson["Expiry"])
        val activation = parseTimestamp(nightWaveJson["Activation"])
        val now = getInstantNow()
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
            activeChallenges = parseChallenges(nightWaveJson["ActiveChallenges"])
        )

        val expire = Duration.between(now, getStartOfNextDay()).seconds
        redisService.setValueWithExpiry(WF_NIGHTWAVE_KEY, nightWaveEntity, expire, TimeUnit.SECONDS)
        return nightWaveEntity
    }


    /**
     * 解析裂缝
     * @param fissureJson 裂缝数据
     * @param isStorm 是否是九重天裂缝
     */
    fun parseFissureArray(fissureJson: JsonNode, isStorm: Boolean = false): List<Fissure> {
        val fissureEntity = fissureJson.map { fissure ->
            val node = redisService.getValueTyped<Nodes>("${WF_MARKET_CACHE_KEY}Node:${fissure["Node"]?.asText()}")
            val expiry = parseTimestamp(fissure["Expiry"])
            val modifierNum =
                redisService.getValueTyped<Modifiers>("${WF_MARKET_CACHE_KEY}FissureModifier:${fissure[if (isStorm) "ActiveMissionTier" else "Modifier"]?.asText()}")
            Fissure(
                id = fissure["_id"]?.get("\$oid")?.asText() ?: "",
                activation = parseTimestamp(fissure["Activation"]),
                expiry = expiry,
                eta = formatDuration(Duration.between(getInstantNow(), expiry)).replace("\\s+".toRegex(), ""),
                node = node!!.name,
                missionType = redisService.getValueTyped<String>("${WF_MARKET_CACHE_KEY}MissionType:${fissure["MissionType"]?.asText()}")
                    ?: node.type
                    ?: redisService.getValueTyped<String>("${WF_MARKET_CACHE_KEY}MissionType:MT_DEFAULT"),
                faction = node.faction?.replaceFaction(),
                modifier = modifierNum!!.value,
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
        if (redisService.hasKey(WF_FISSURE_KEY)) return redisService.getValueTyped<List<Fissure?>>(WF_FISSURE_KEY)

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
            redisService.setValueWithExpiry(WF_FISSURE_KEY, filteredFissureList, expire, TimeUnit.SECONDS)
            filteredFissureList
        }
    }

    /**
     * 解析虚空交易
     * @param voidTradersJsonNode 虚空商人Json数据Node
     */
    fun parseVoidTraders(voidTradersJsonNode: JsonNode): List<VoidTrader>? {
        if (redisService.hasKey(WF_VOIDTRADER_KEY)) return redisService.getValueTyped<List<VoidTrader>>(
            WF_VOIDTRADER_KEY
        )
        val untranslatedItems = mutableMapOf<String, String>()
        val translatedItems = mutableMapOf<String, String>()
        val englishOnlyPattern = Regex("^[a-zA-Z\\s\\-.'·]+$")
        val itemMap = mapOf(
            "Skin" to "外观",
            "New Year Free" to "迎新春",
            "Sigil" to "纹章",
            "Glyph" to "浮印",
            "Display" to "展示图",
            "Booster" to "加成",
            "Weapon" to "武器",
            "Badge Item" to "徽章",
            "<ARCHWING>" to ""
        )

        val voidTradersList = voidTradersJsonNode.map { voidTrader ->
            val activationTime = parseTimestamp(voidTrader["Activation"])
            val isActive = activationTime?.let {
                getInstantNow().isAfter(it)
            } ?: false

            // 先收集所有需要翻译的物品ID
            val itemsToTranslate = mutableListOf<String>()
            voidTrader["Manifest"]?.forEach { item ->
                val voidItem = item["ItemType"]?.asText() ?: ""
                itemsToTranslate.add(voidItem)

                // 从缓存获取翻译
                val translatedValue =
                    redisService.getValueTyped<Info>("${WF_MARKET_CACHE_KEY}Languages:${voidItem.lowercase()}")?.value

                if (translatedValue != null && !englishOnlyPattern.matches(translatedValue)) {
                    translatedItems[voidItem] = translatedValue
                } else {
                    translatedValue?.let { untranslatedItems[voidItem] = it }
                }
            }

            // 尝试从数据库获取未翻译项的翻译
            if (untranslatedItems.isNotEmpty()) {
                // untranslatedItems的value是英文名，需要获取英文名到中文名的映射
                val translatedDbItems = wfLexiconService.getZhNamesMap(untranslatedItems.values.toList())

                // 遍历untranslatedItems，建立voidItem到中文翻译的映射
                untranslatedItems.forEach { (voidItem, englishName) ->
                    // 使用英文名获取中文翻译
                    val chineseName = translatedDbItems[englishName.lowercase()]
                    // 选择最优的翻译：中文 > 英文 > 格式化名称
                    translatedItems[voidItem] = chineseName ?: englishName
                }
            }


            // 现在再创建物品列表，此时translatedItems已包含所有可能的翻译
            val inventory = voidTrader["Manifest"]?.let { manifest ->
                val items = manifest.map { voidTraderItem ->
                    val voidItem = voidTraderItem["ItemType"]?.asText() ?: ""
                    val formattedItem = StringUtils.formatWithSpaces(voidItem.split("/").lastOrNull() ?: "")

                    // 获取翻译后的物品名称
                    val baseItemName = (translatedItems[voidItem] ?: formattedItem)

                    // 检查并替换itemMap中定义的关键词
                    val finalItemName = itemMap.entries.fold(baseItemName) { itemName, (key, value) ->
                        itemName.replace(key, value)
                    }

                    VoidTraderItem(
                        item = finalItemName,
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
                node = redisService.getValueTyped<Nodes>("${WF_MARKET_CACHE_KEY}Node:${voidTrader["Node"]?.asText()}")?.name
                    ?: voidTrader["Node"]?.asText(),
                inventory = inventory
            )
        }

        val expire = voidTradersList
            .minOfOrNull { it.eta?.parseDuration() ?: Long.MAX_VALUE }
            ?.coerceAtLeast(30) ?: 300
        redisService.setValueWithExpiry(WF_VOIDTRADER_KEY, voidTradersList, expire, TimeUnit.SECONDS)
        return voidTradersList
    }

    /**
     * 解析圣殿结合仪式目标信息
     * @param simarisJson 圣殿结合仪式目标Json
     */
    fun parseSimaris(simarisJson: JsonNode): Simaris? {
        if (redisService.hasKey(WF_SIMARIS_KEY)) return redisService.getValueTyped<Simaris>(WF_SIMARIS_KEY)
        val targetItem = simarisJson["LastCompletedTargetType"].textValue().lowercase()
        var target = wfUtil.getLanguageValue(targetItem) ?: return null
        if (target == "远古堕落者") target = "corrupted_ancient"
        target = target.formatSpacesToUnderline().lowercase()
        val simarisPersistent =
            redisService.getValueTyped<SimarisPersistent>("${WF_MARKET_CACHE_KEY}SimarisPersistent:${target}")
                ?: return null
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
        redisService.setValueWithExpiry(WF_SIMARIS_KEY, simaris, expire, TimeUnit.SECONDS)
        return simaris
    }

    /**
     * 解析入侵信息
     * @param invasionsJson 入侵信息Json
     */
    fun parseInvasions(invasionsJson: JsonNode): List<Invasions>? {
        if (redisService.hasKey(WF_INVASIONS_KEY)) return redisService.getValueTyped<List<Invasions>>(WF_INVASIONS_KEY)
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
                desc = wfUtil.getLanguageValue(invasions["LocTag"].asText().lowercase()),
                faction = faction.replaceFaction(),
                defenderFaction = invasions["DefenderFaction"].textValue().replaceFaction(),
                node = redisService.getValueTyped<Nodes>("${WF_MARKET_CACHE_KEY}Node:${invasions["Node"]?.asText()}")?.name
                    ?: invasions["Node"]?.asText(),
                count = count,
                requiredRuns = requiredRuns,
                completion = if (vsInfestation) (1 + count.toDouble() / requiredRuns.toDouble()) * 100 else (1 + count.toDouble() / requiredRuns.toDouble()) * 50,
                completed = invasions["Completed"].booleanValue(),
                vsInfestation = vsInfestation,
                attackerReward = if (invasions["AttackerReward"].has("countedItems")) invasions["AttackerReward"]["countedItems"].map { item ->
                    Modifiers(
                        wfUtil.getLanguageValue(item["ItemType"].asText().lowercase()),
                        item["ItemCount"].intValue()
                    )
                } else null,
                defenderReward = invasions["DefenderReward"]["countedItems"].map { item ->
                    Modifiers(
                        wfUtil.getLanguageValue(item["ItemType"].asText().lowercase()),
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
        redisService.setValueWithExpiry(WF_INVASIONS_KEY, completedInvasions, expire, TimeUnit.MINUTES)
        return completedInvasions
    }

    /**
     * 解析DE紫卡周榜信息
     */
    fun parseWeeklyRiven() {
        if (redisService.hasKey(WF_RIVEN_UN_REROLLED_KEY) && redisService.hasKey(WF_RIVEN_REROLLED_KEY)) return
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

        // itemType到中文的映射
        val itemTypeToChineseMap = mapOf(
            "Rifle Riven Mod" to "步枪未开",
            "Pistol Riven Mod" to "手枪未开",
            "Melee Riven Mod" to "近战未开",
            "Shotgun Riven Mod" to "霰弹枪未开",
            "Kitgun Riven Mod" to "组合枪未开",
            "Zaw Riven Mod" to "Zaw未开",
            "Archgun Riven Mod" to "Archgun未开"
        )

        // 批量获取所有需要转换的compatibility值
        val compatibilityValues = rawRivenList.mapNotNull { it.compatibility }
        val compatibilityMap = if (compatibilityValues.isNotEmpty()) {
            if (redisService.hasKey(WF_MARKET_RIVEN_KEY)) {
                val entities = redisService.getValueTyped<List<WfRivenEntity>>(WF_MARKET_RIVEN_KEY)
                entities?.associate { it.enName to it.zhName } ?: emptyMap()
            } else {
                // 查询数据库获取所有compatibility对应的中文名
                val entities = wfRivenService.selectAllRivenData()
                entities.associate { it.enName to it.zhName }
            }
        } else {
            emptyMap()
        }

        // 使用映射表转换compatibility值
        val rivenList = rawRivenList.map { riven ->
            val compatibility = when {
                riven.compatibility != null -> {
                    compatibilityMap[riven.compatibility] ?: riven.compatibility
                }

                itemTypeToChineseMap.containsKey(riven.itemType) -> {
                    itemTypeToChineseMap[riven.itemType]
                }

                else -> null
            }
            riven.copy(compatibility = compatibility)
        }

        // 根据rerolled字段将列表分成两个列表
        val rerolledList = rivenList.filter { it.rerolled == true }
        val unRerolledList = rivenList.filter { it.rerolled == false }

        val expire = Duration.between(Instant.now(), getNextMonday()).seconds

        redisService.setValueWithExpiry(WF_RIVEN_UN_REROLLED_KEY, unRerolledList, expire, TimeUnit.SECONDS)
        redisService.setValueWithExpiry(WF_RIVEN_REROLLED_KEY, rerolledList, expire, TimeUnit.SECONDS)
    }

    /**
     * 解析回廊相关信息
     */
    fun parseIncarnon(): Incarnon? {
        if (redisService.hasKey(WF_INCARNON_KEY)) return redisService.getValueTyped<Incarnon>(WF_INCARNON_KEY)
        val expire = Duration.between(Instant.now(), getNextMonday()).seconds

        val incarnonJson = JacksonUtil.readTree(File(WARFRAME_INCARNON))
        val ordinaryJson = incarnonJson["ordinary"]
        val steelJson = incarnonJson["steel"]
        val ordinaryStart = Instant.parse(ordinaryJson[0]["startTime"].textValue())
        val steelStart = Instant.parse(steelJson[0]["startTime"].textValue())

        // 获取到现在经过的秒数
        val now = Instant.now()
        val ordinarySeconds = Duration.between(ordinaryStart, now).seconds
        val steelSeconds = Duration.between(steelStart, now).seconds
        val sevenDays = 604800
        val ordinarySize = ordinaryJson.size()
        val steelSize = steelJson.size()
        val ordinaryWeeks = ordinarySize * sevenDays
        val steelWeeks = steelSize * sevenDays
        // 使用模运算和除法计算当前处于第几个7天周期
        val ordinaryInd = ((ordinarySeconds % ordinaryWeeks) / sevenDays).toInt()
        val steelInd = ((steelSeconds % steelWeeks) / sevenDays).toInt()

        // 计算下周的索引
        val ordinaryNextInd = (ordinaryInd + 1) % ordinarySize
        val steelNextInd = (steelInd + 1) % steelSize

        val activation = getFirstDayOfWeek()
        val expiry = getLastDayOfWeek()

        // 缓存获取灵化武器紫卡映射
        val rivenMap = if (redisService.hasKey(WF_MARKET_RIVEN_KEY)) {
            val entities = redisService.getValueTyped<List<WfRivenEntity>>(WF_MARKET_RIVEN_KEY)
            entities?.associate { it.urlName to it.zhName } ?: emptyMap()
        } else {
            // 查询数据库获取所有Riven数据
            val entities = wfRivenService.selectAllRivenData()
            val map = entities.associate { it.urlName to it.zhName }
            redisService.setValue(WF_MARKET_RIVEN_KEY, entities)
            map
        }

        val rivenPriceList = redisService.getValueTyped<List<Riven>>(WF_RIVEN_UN_REROLLED_KEY)

        fun processSteelItems(steelJsonNode: JsonNode): List<Incarnon.SteelItem> {
            return steelJsonNode["items"].map { item ->
                val urlName = item["url_name"].textValue()
                val zhName = rivenMap[urlName]
                Incarnon.SteelItem(
                    name = item["name"].textValue(),
                    riven = item["riven"].doubleValue(),
                    urlName = urlName,
                    rivenPrice = rivenPriceList?.find { it.compatibility == zhName }?.median ?: 0.0,
                )
            }.toList()
        }

        val incarnon = Incarnon(
            thisWeek = Incarnon.IncarnonData(
                ordinary = Incarnon.WeekData(
                    week = ordinaryInd + 1,
                    items = ordinaryJson[ordinaryInd]["items"].map { it.textValue() },
                ),
                steel = Incarnon.WeekData(
                    week = steelInd + 1,
                    items = processSteelItems(steelJson[steelInd]),
                )
            ),
            nextWeek = Incarnon.IncarnonData(
                ordinary = Incarnon.WeekData(
                    week = ordinaryNextInd + 1,
                    items = ordinaryJson[ordinaryNextInd]["items"].map { it.textValue() },
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

        redisService.setValueWithExpiry(WF_INCARNON_KEY, incarnon, expire, TimeUnit.SECONDS)
        return incarnon
    }

    fun parseWmMinimalPrice(key: String): Int {
        val json = HttpUtil.doGetJson(url = "$WARFRAME_MARKET_ITEMS_ORDERS_V2/$key")
        val wmData = json["data"]
        val filterData = wmData.filter { it["type"].textValue() == "sell" }
        if (filterData.isEmpty()) {
            return 0
        }
        val onlineOrders = filterData.filter { it["user"]["status"].textValue() != "offline" }

        val targetOrders = onlineOrders.ifEmpty { filterData }

        val minimalOrder = targetOrders.minByOrNull { it["platinum"].intValue() } ?: return 0
        val price = minimalOrder["platinum"].intValue()
        return price
    }
}