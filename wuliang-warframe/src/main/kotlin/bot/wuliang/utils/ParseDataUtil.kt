package bot.wuliang.utils

import bot.wuliang.config.WfMarketConfig.WF_ARCHONHUNT_KEY
import bot.wuliang.config.WfMarketConfig.WF_FISSURE_KEY
import bot.wuliang.config.WfMarketConfig.WF_MARKET_CACHE_KEY
import bot.wuliang.config.WfMarketConfig.WF_NIGHTWAVE_KEY
import bot.wuliang.config.WfMarketConfig.WF_SIMARIS_KEY
import bot.wuliang.config.WfMarketConfig.WF_SORTIE_KEY
import bot.wuliang.config.WfMarketConfig.WF_STEELPATH_KEY
import bot.wuliang.config.WfMarketConfig.WF_VOIDTRADER_KEY
import bot.wuliang.jacksonUtil.JacksonUtil
import bot.wuliang.moudles.*
import bot.wuliang.redis.RedisService
import bot.wuliang.service.WfLexiconService
import bot.wuliang.utils.StringUtils.formatSpacesToUnderline
import bot.wuliang.utils.TimeUtils.formatDuration
import bot.wuliang.utils.TimeUtils.getInstantNow
import bot.wuliang.utils.TimeUtils.getLastDayOfWeek
import bot.wuliang.utils.TimeUtils.getStartOfDay
import bot.wuliang.utils.TimeUtils.getStartOfNextDay
import bot.wuliang.utils.TimeUtils.getTimeOfNextDay
import bot.wuliang.utils.TimeUtils.getFirstDayOfWeek
import bot.wuliang.utils.TimeUtils.parseDuration
import bot.wuliang.utils.WfStatus.replaceFaction
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.time.Duration
import java.time.Instant
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

@Component
class ParseDataUtil {
    @Autowired
    private lateinit var redisService: RedisService

    @Autowired
    private lateinit var wfLexiconService: WfLexiconService

    /**
     * 通用解析常规突击任务（每日突击与周突击）
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
            variants = JacksonUtil.parseArray({ variant ->
                Variants(
                    missionType = redisService.getValueTyped<String>("${keyPrefix}MissionType:${variant["missionType"]?.asText()}"),
                    modifierType = redisService.getValueTyped<String>("${keyPrefix}ModifierType:${variant["modifierType"]?.asText()}"),
                    node = redisService.getValueTyped<Nodes>("${keyPrefix}Node:${variant["node"]?.asText()}")!!.name
                )
            }, sortie[missionKey])
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

    @Scheduled(cron = "1 0 8 * * 1")
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

    private fun parseChallenges(challengesNode: JsonNode?): List<Challenges> {
        return challengesNode?.let {
            JacksonUtil.parseArray({ challenge ->
                val challengeText = challenge["Challenge"]?.asText() ?: ""
                val isDaily = challenge["Daily"]?.asBoolean() ?: false
                val isElite = challengeText.lowercase().contains("hard")

                Challenges(
                    id = challenge["_id"]?.get("\$oid")?.asText() ?: "",
                    isDaily = isDaily,
                    isElite = isElite,
                    isPermanent = challenge["Permanent"]?.asBoolean() ?: false,
                    title = redisService.getValueTyped<Info>("${WF_MARKET_CACHE_KEY}Languages:${challengeText.lowercase()}")?.value
                        ?: StringUtils.formatWithSpaces(challengeText.split("/").lastOrNull() ?: ""),
                    desc = redisService.getValueTyped<Info>("${WF_MARKET_CACHE_KEY}Languages:${challengeText.lowercase()}")?.desc
                        ?: StringUtils.formatWithSpaces(challengeText.split("/").lastOrNull() ?: ""),
                    reputation = when {
                        isDaily -> 1000
                        isElite -> 7000
                        else -> 4500
                    }
                )
            }, it)
        } ?: emptyList()
    }


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

    fun parseFissureArray(fissureJson: JsonNode, isStorm: Boolean = false): List<Fissure> {
        val fissureEntity = JacksonUtil.parseArray({ fissure ->
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
        }, fissureJson)
        return fissureEntity
    }

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

    fun parseVoidTraders(voidTradersJsonNode: JsonNode): List<VoidTrader>? {
        if (redisService.hasKey(WF_VOIDTRADER_KEY)) return redisService.getValueTyped<List<VoidTrader>>(WF_VOIDTRADER_KEY)
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

        val voidTradersList = JacksonUtil.parseArray({ voidTrader ->
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
                val items = JacksonUtil.parseArray({ voidTraderItem ->
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
                }, manifest)
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
        }, voidTradersJsonNode)
        val expire = voidTradersList
            .minOfOrNull { it.eta?.parseDuration() ?: Long.MAX_VALUE }
            ?.coerceAtLeast(30) ?: 300
        redisService.setValueWithExpiry(WF_VOIDTRADER_KEY, voidTradersList, expire, TimeUnit.SECONDS)
        return voidTradersList
    }

    fun parseSimaris(simarisJson: JsonNode): Simaris? {
        if (redisService.hasKey(WF_SIMARIS_KEY)) return redisService.getValueTyped<Simaris>(WF_SIMARIS_KEY)
        val targetItem = simarisJson["LastCompletedTargetType"].textValue().lowercase()
        val target = redisService.getValueTyped<Info>("${WF_MARKET_CACHE_KEY}Languages:${targetItem}") ?: return null
        var targetValue = target.value!!
        if (targetValue == "远古堕落者") targetValue = "corrupted_ancient"
        targetValue = targetValue.formatSpacesToUnderline().lowercase()
        val simarisPersistent =
            redisService.getValueTyped<SimarisPersistent>("${WF_MARKET_CACHE_KEY}SimarisPersistent:${targetValue}") ?: return null
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
}