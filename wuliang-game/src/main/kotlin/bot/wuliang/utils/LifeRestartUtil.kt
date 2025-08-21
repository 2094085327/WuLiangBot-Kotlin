package bot.wuliang.utils

import bot.wuliang.botLog.logUtil.LoggerUtils.logError
import bot.wuliang.botUtil.BotUtils
import bot.wuliang.config.*
import bot.wuliang.entity.UserInfoEntity
import bot.wuliang.entity.vo.AgeDataVO
import bot.wuliang.entity.vo.EventDataVO
import bot.wuliang.entity.vo.JudgeItemVO
import bot.wuliang.entity.vo.TalentDataVo
import bot.wuliang.exception.RespBean
import bot.wuliang.imageProcess.WebImgUtil
import bot.wuliang.jacksonUtil.JacksonUtil
import bot.wuliang.redis.RedisService
import bot.wuliang.respEnum.RestartRespEnum
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.io.File
import java.time.Duration
import java.time.temporal.ChronoUnit
import java.util.*
import java.util.concurrent.TimeUnit
import java.util.regex.Matcher
import kotlin.math.floor
import kotlin.random.Random


/**
 * @description: 人生重开用到的方法
 * @author Nature Zero
 * @date 2024/2/16 13:24
 */
@Component
class LifeRestartUtil @Autowired constructor(
    private val redisService: RedisService,
    private val webImgUtil: WebImgUtil
) {

    /**
     * 更新Redis用户信息
     *
     * @param userInfo 用户信息
     */
    fun updateUserInfo(userInfo: UserInfoEntity) {
        redisService.setValueWithExpiry("lifeRestart:userInfo:${userInfo.userId}", userInfo, 5L, TimeUnit.MINUTES)
    }

    /**
     * 查找用户信息
     *
     * @param realId 真实id
     * @return 找到的用户信息
     */
    fun findUserInfo(realId: String): UserInfoEntity? {
        redisService.setExpire("lifeRestart:userInfo:${realId}", Duration.of(5L, ChronoUnit.MINUTES))
        return redisService.getValueTyped<UserInfoEntity>("lifeRestart:userInfo:${realId}")
    }


    /**
     * 获取并更新数据
     *
     * @return 判断是否有文件缺失
     */
    fun fetchDataAndUpdateLists(): RespBean {

        fun readData(filePath: String, missingMessage: String): JsonNode? {
            return if (File(filePath).exists()) {
                JacksonUtil.getJsonNode(filePath)
            } else {
                logError(missingMessage)
                null
            }
        }

        val ageData = readData(AGE_JSONPATH, AGE_JSON_MISS)
        val eventData = readData(EVENT_JSONPATH, EVENT_JSON_MISS)
        val talentData = readData(TALENT_JSONPATH, TALENT_JSON_MISS)

        if (ageData == null || eventData == null || talentData == null) return RespBean.error(RestartRespEnum.DATA_MISSING)

        // 将数据写入redis
        redisService.setValueWithExpiry("lifeRestart:ageData", ageData.toString(), 10L, TimeUnit.MINUTES)
        redisService.setValueWithExpiry("lifeRestart:eventData", eventData.toString(), 10L, TimeUnit.MINUTES)
        redisService.setValueWithExpiry("lifeRestart:talentData", talentData.toString(), 10L, TimeUnit.MINUTES)

        return RespBean.success()
    }

    /**
     * 初始化数据
     *
     * @return 响应
     */
    fun getFetchData(): RespBean {
        val ageDataBoolean = redisService.setExpire("lifeRestart:ageData", Duration.of(10L, ChronoUnit.MINUTES))
        val eventDataBoolean = redisService.setExpire("lifeRestart:eventData", Duration.of(10L, ChronoUnit.MINUTES))
        val talentDataBoolean = redisService.setExpire("lifeRestart:talentData", Duration.of(10L, ChronoUnit.MINUTES))

        return if (!ageDataBoolean || !eventDataBoolean || !talentDataBoolean) fetchDataAndUpdateLists()
        else RespBean.success()
    }

    /**
     * 分配默认属性与随机额外的属性
     *
     * @param userInfo 用户信息
     * @param attributeNames 属性名数组
     * @param mutableProperty 属性
     * @return 属性
     */
    private fun additionalAttributes(
        userInfo: UserInfoEntity,
        attributeNames: List<String>,
        mutableProperty: MutableMap<String, Int>
    ): MutableMap<String, Int> {
        // 初始化快乐和生命
        mutableProperty[LIF] = (userInfo.property[LIF] ?: 0) + 1
        mutableProperty[SPR] = (userInfo.property[SPR] ?: 0) + 5

        // 如果存在随机属性，则将其加到随机的对应属性上
        userInfo.property[RDM]?.let { rdmValue ->
            val randomAttribute = attributeNames.random()
            mutableProperty[randomAttribute] = (mutableProperty[randomAttribute] ?: 0) + rdmValue
        }

        return mutableProperty
    }


    /**
     * 随机生成属性
     *
     * @param userInfo 用户信息
     * @return 属性
     */
    fun randomAttributes(userInfo: UserInfoEntity): MutableMap<String, Int> {
        var mutableProperty = userInfo.property
        val userMaxProperty = userInfo.maxProperty

        // 随机选择第一个字段并赋值
        val attributeNames = listOf(CHR, INT, STR, MNY)
        val firstAttributeName = attributeNames.shuffled().first()
        mutableProperty[firstAttributeName] = (0..10).random()

        // 随机选择剩下的字段
        val remainingAttributes = attributeNames - firstAttributeName
        var remainingSum = userInfo.status - (mutableProperty[firstAttributeName] ?: 0)

        remainingAttributes.shuffled().forEachIndexed { index, attributeName ->
            val maxPossibleValue = minOf(remainingSum, 10)
            val value = if (index == remainingAttributes.lastIndex) maxPossibleValue
            else (0..maxPossibleValue).random()

            mutableProperty[attributeName] = (mutableProperty[attributeName] ?: 0) + value
            remainingSum -= value
        }
        // 调用 additionalAttributes 更新属性
        mutableProperty = additionalAttributes(userInfo, attributeNames, mutableProperty)

        // 初始化最大属性值
        attributeNames.forEach { key ->
            userMaxProperty[key] = mutableProperty[key] ?: 0
        }

        // 计算总评
        userMaxProperty[SUM] = floor(
            (userMaxProperty.filterKeys { it != SUM }.values.sum() * 2 + userInfo.age / 2).toDouble()
        ).toInt()

        updateUserInfo(userInfo)
        return mutableProperty
    }


    /**
     * 手动分配属性
     *
     * @param match 匹配
     * @return 属性
     */
    fun assignAttributes(userInfo: UserInfoEntity, match: Matcher): RestartRespEnum {
        val attributeValues = match.group(1).split(" ").map(String::toInt)
        val total = attributeValues.sum()

        // 检查输入的总和是否超过角色的剩余点数
        if (total > userInfo.status) return RestartRespEnum.SIZE_OUT_ERROR
        // 属性值分配最大为10
        if (attributeValues.any { it > 10 }) return RestartRespEnum.VALUE_OUT_ERROR

        val mutableProperty = userInfo.property
        val attributeNames = listOf(CHR, INT, STR, MNY)

        attributeNames.forEachIndexed { index, attributeName ->
            mutableProperty[attributeName] = (mutableProperty[attributeName] ?: 0) + attributeValues[index]
        }

        additionalAttributes(userInfo, attributeNames, mutableProperty)

        // 初始化最大属性值
        attributeNames.forEach { key ->
            userInfo.maxProperty[key] = mutableProperty[key] ?: 0
        }

        // 计算总评
        userInfo.maxProperty[SUM] = floor(
            (userInfo.maxProperty.values.sum() * 2 + userInfo.age / 2).toDouble()
        ).toInt()

        updateUserInfo(userInfo)
        return RestartRespEnum.SUCCESS
    }


    /**
     * 使用游戏购买额外属性
     * TODO
     */
    @Suppress("unused")
    fun getAddAttributes() {

    }

    /**
     * 判断属性是否满足条件
     *
     * @param userInfo 用户信息
     * @param condition 条件
     * @return 是否满足条件
     */
    private fun checkProp(userInfo: UserInfoEntity, condition: String): Boolean {
        val property = userInfo.property
        val events = userInfo.events
        val length = condition.length
        var i = condition.indexOfFirst { it in listOf('>', '<', '!', '?', '=') }

        // 要求的属性
        val prop = condition.substring(0, i)
        val symbol =
            if (condition[i + 1] == '=') condition.substring(i, i + 2).also { i++ } else condition[i].toString()
        val d = condition.substring(i + 1, length)


        // 如果是列表，就转换成列表，否则转换成整数
        val propData = property[prop]
        val conditionData: Any = if (d.startsWith("[")) {
            d.substring(1, d.length - 1).split(",").map { it.trim() }
        } else d.toInt()


        return when (prop) {
            EVT -> {
                conditionData as List<*>
                when (symbol) {
                    "?" -> events.any { it in conditionData }
                    "!" -> events.none { it in conditionData }
                    else -> false
                }
            }

            AGE -> {
                conditionData as List<*>
                when (symbol) {
                    "!" -> conditionData.none { it.toString().toInt() == userInfo.age }
                    "?" -> conditionData.all { it.toString().toInt() == userInfo.age }
                    else -> false
                }
            }

            TLT -> {
                conditionData as List<*>
                when (symbol) {
                    "?" -> userInfo.talent.map { it.id }.any { it in conditionData }
                    "!" -> userInfo.talent.map { it.id }.none { it in conditionData }
                    else -> false
                }
            }

            else -> {
                conditionData as Int
                when (symbol) {
                    ">" -> (propData ?: 0) > conditionData
                    "<" -> (propData ?: 0) < conditionData
                    else -> false
                }
            }
        }
    }

    /**
     * 解析条件
     *
     * @param condition 条件
     * @return 解析后的条件
     */
    private fun parseCondition(condition: String): List<Any> {
        val conditions = mutableListOf<Any>()
        val stack = mutableListOf(conditions)
        var cursor = 0

        fun catchString(i: Int) {

            val str = condition.substring(cursor, i).trim()

            cursor = i

            if (str.isNotEmpty()) {
                stack[0].add(str)
            }

        }

        for (i in condition.indices) {
            when (condition[i]) {
                ' ' -> continue
                '(' -> {
                    catchString(i)
                    cursor++
                    val sub = mutableListOf<Any>()
                    stack[0].add(sub)
                    stack.add(0, sub)
                }

                ')' -> {
                    catchString(i)
                    cursor++
                    stack.removeAt(0)
                }

                '|', '&' -> {
                    catchString(i)
                    catchString(i + 1)
                }

                else -> continue
            }
        }

        catchString(condition.length)
        return conditions
    }

    /**
     * 检查分析的条件
     *
     * @param userInfo 用户信息
     * @param conditions 条件
     * @return 是否满足条件
     */
    private fun checkParsedConditions(userInfo: UserInfoEntity, conditions: Any): Boolean {
        if (conditions !is List<*>) {
            conditions as String
            return checkProp(userInfo, conditions)
        }
        if (conditions.isEmpty()) return true
        if (conditions.size == 1) return checkParsedConditions(userInfo, conditions[0]!!)

        var ret = checkParsedConditions(userInfo, conditions[0]!!)
        for (i in 1 until conditions.size step 2) {
            when (conditions[i] as String) {
                "&" ->
                    if (ret)
                        ret = checkParsedConditions(userInfo, conditions[i + 1]!!)

                "|" ->
                    if (ret) return true
                    else ret = checkParsedConditions(userInfo, conditions[i + 1]!!)

                else -> return false
            }
        }

        return ret
    }

    /**
     * 检查条件
     *
     * @param userInfo 用户信息
     * @param condition 条件
     * @return
     */
    private fun checkCondition(userInfo: UserInfoEntity, condition: String): Boolean {
        val parsedCondition = parseCondition(condition)
        return checkParsedConditions(userInfo, parsedCondition)
    }

    /**
     * 找出当前年龄下所有符合条件的事件
     *
     * @param userInfo 用户信息
     * @param ageDataVO 年龄列表
     * @return 符合条件的事件列表
     */
    private fun generateValidEvent(
        userInfo: UserInfoEntity,
        ageDataVO: AgeDataVO,
        eventData: JsonNode
    ): List<Map<String, Double>> {
        return ageDataVO.eventList?.mapNotNull { event ->
            event?.let {
                val splitEvent = it.toString().split("*").map(String::toDouble)
                val eventId = splitEvent[0].toInt().toString()
                val weight = if (splitEvent.size == 1) 1.0 else splitEvent[1]
                if (eventCheck(userInfo = userInfo, eventId = eventId, eventData = eventData)) {
                    mapOf(eventId to weight)
                } else null
            }
        } ?: emptyList()
    }

    /**
     * 加权随机
     *
     * @param list 待随机的事件列表
     * @return 随机的事件ID
     */
    private fun weightRandom(list: List<Map<String, Double>>): String {
        val totalWeights = list.sumOf { it.values.first() }
        var random = Random.nextDouble() * totalWeights
        for (item in list) {
            val weight = item.values.first()
            random -= weight
            if ((random) < 0) {
                return item.keys.first()
            }
        }
        return list.last().keys.first()
    }

    /**
     * 获取随机事件
     *
     * @param userInfo 用户信息
     * @return 随机事件ID
     */
    private fun getRandom(userInfo: UserInfoEntity, ageData: JsonNode, eventData: JsonNode): String {

        val ageList = ageData.get(userInfo.age.toString())
        val eventList: MutableList<Any?> = mutableListOf()
        for (element in ageList[TYPE_EVENT]) eventList.add(element.asText()) // 使用 asText() 方法
        val ageDataVO = AgeDataVO(age = userInfo.age, eventList = eventList)

        // 当前年龄下所有符合条件的事件
        val eventListCheck = generateValidEvent(userInfo, ageDataVO, eventData)
        return weightRandom(eventListCheck)
    }

    /**
     * 判断游戏是否结束（生命值小于1）
     *
     * @param userInfo 用户信息
     * @return 是否结束
     */
    private fun isEnd(userInfo: UserInfoEntity) {
        val lif = userInfo.property[LIF] ?: 1
        if (lif < 1) userInfo.isEnd = true
    }

    /**
     * 下一岁年龄
     *
     * @param userInfo 用户信息
     */
    private fun ageNext(userInfo: UserInfoEntity) {
        userInfo.age += 1
    }

    /**
     * 获取事件
     *
     * @param userInfo 用户信息
     * @param eventId 事件ID
     * @return 事件
     */
    private fun getDo(userInfo: UserInfoEntity, eventId: String, eventData: JsonNode): List<Any?> {
        val eventDataJson = eventData.get(eventId)
        val eventEffect = eventDataJson["effect"]
        val event = EventDataVO(
            id = eventId,
            event = eventDataJson[TYPE_EVENT].textValue(),
            grade = eventDataJson["grade"]?.intValue(),
            postEvent = eventDataJson["postEvent"]?.textValue(),
            effectChr = eventEffect?.get(CHR)?.intValue(),
            effectInt = eventEffect?.get(INT)?.intValue(),
            effectStr = eventEffect?.get(STR)?.intValue(),
            effectMny = eventEffect?.get(MNY)?.intValue(),
            effectSpr = eventEffect?.get(SPR)?.intValue(),
            effectLif = eventEffect?.get(LIF)?.intValue(),
            effectAge = eventEffect?.get(AGE)?.intValue(),
            noRandom = eventDataJson["NoRandom"]?.intValue(),
            include = eventDataJson["include"]?.textValue(),
            exclude = eventDataJson["exclude"]?.textValue(),
            branch = eventDataJson["branch"]?.mapNotNull { it?.textValue() }?.toMutableList()
        )
        val branchItem = eventDataJson["branch"]?.filterNotNull()?.firstOrNull { branch ->
            val cond = branch.textValue().split(":").firstOrNull()
            cond?.let { thisCond -> checkCondition(userInfo, thisCond) } == true
        }?.textValue()

        return if (branchItem != null) {
            listOf(event) + branchItem.split(":").getOrNull(1)
        } else {
            listOf(event)
        }
    }

    data class SendListEntity(
        val event: EventDataVO? = null,
        val age: Int? = 0,
    )

    /**
     * 执行事件
     *
     * @param userInfo
     * @param eventId
     * @return 事件内容
     */
    private fun doEvent(
        userInfo: UserInfoEntity,
        eventId: String,
        eventDataJson: JsonNode
    ): List<SendListEntity> {
        val eventData = getDo(userInfo, eventId, eventDataJson)
        val event = eventData[0] as EventDataVO
        val property = userInfo.property

        // 修改生命值和游戏年龄
        event.effectLif?.let { property[LIF] = property[LIF]?.plus(it) ?: it }
        event.effectAge?.let { userInfo.age += it }

        // 激活天赋
        event.activeTalent = launchTalent(userInfo)

        // 更新属性和计算总评
        listOf(CHR, INT, STR, MNY, SPR).forEach { key ->
            val value = when (key) {
                CHR -> event.effectChr ?: 0
                INT -> event.effectInt ?: 0
                STR -> event.effectStr ?: 0
                MNY -> event.effectMny ?: 0
                SPR -> event.effectSpr ?: 0
                else -> 0
            }
            // 更新用户属性
            property[key] = property[key]?.plus(value) ?: value
            // 更新最大属性
            val userMaxProperty = userInfo.maxProperty
            userMaxProperty[key] = (property[key] as Int).coerceAtLeast(userMaxProperty[key] as Int)

            // 计算总评
            userMaxProperty[SUM] = floor(
                (userMaxProperty.filterKeys { it != SUM }.values.sum() * 2 + userInfo.age / 2).toDouble()
            ).toInt()


            event.eachChange[key] = property[key] as Int
        }

        // 记录事件
        event.id?.let { userInfo.events.add(it) }

        val sendListEntity = SendListEntity(event = event, age = userInfo.age)

        return if (eventData.size == 1) {
            listOf(sendListEntity)
        } else {
            val nextEventId = eventData[1] as String
            listOf(sendListEntity) + doEvent(userInfo, nextEventId, eventDataJson)
        }
    }


    /**
     * 下一步
     *
     * @param userInfo 用户信息
     */
    fun next(
        userInfo: UserInfoEntity,
        ageData: JsonNode,
        eventData: JsonNode
    ): List<SendListEntity> {
        ageNext(userInfo)

        val eventContent = doEvent(userInfo, getRandom(userInfo, ageData, eventData), eventData)
        isEnd(userInfo)
        return eventContent
    }

    /**
     * 重开运行
     *
     * @param userInfo 用户信息
     * @return 事件内容
     */
    fun trajectory(
        userInfo: UserInfoEntity,
        ageData: JsonNode,
        eventData: JsonNode
    ): List<SendListEntity> {
        val trajectory = next(userInfo, ageData, eventData)
        updateUserInfo(userInfo)
        return trajectory
    }

    /**
     * 事件检查
     *
     * @param userInfo 用户信息
     * @param eventId 事件ID
     * @return 是否满足条件
     */
    private fun eventCheck(userInfo: UserInfoEntity, eventId: String, eventData: JsonNode): Boolean {
        val eventList = eventData.get(eventId)

        if (eventList["noRandom"] != null) return false
        if (eventList["exclude"] != null && checkCondition(
                userInfo,
                eventList["exclude"].textValue()
            )
        ) return false
        if (eventList.get("include") != null) return checkCondition(
            userInfo,
            eventList.get("include").textValue()
        )

        return true
    }

    private val talentConfig = TalentConfig()

    /**
     * 人生重开天赋配置
     *
     * @param userInfo 用户信息
     * @return 天赋列表
     */
    fun talentRandomInit(userInfo: UserInfoEntity): MutableList<TalentDataVo> {
        // 继承的天赋
        val lastExtentTalent = null
        val talentRandom = talentRandom(lastExtentTalent, userInfo)
        userInfo.randomTalentTemp = talentRandom
        // 更新用户信息
        updateUserInfo(userInfo)

        return talentRandom
    }


    /**
     * 随机天赋
     *
     * @param include 包含的天赋
     * @param userInfo 用户信息
     * @return 天赋列表
     */
    private fun talentRandom(
        include: TalentDataVo?,
        userInfo: UserInfoEntity
    ): MutableList<TalentDataVo> {
        val rate = getRate(userInfo)

        fun randomGrade(): Int {
            val randomNumber = (0 until rate["total"]!!).random()
            return if ((randomNumber - rate[3]!!) < 0) 3
            else if ((randomNumber - rate[2]!!) < 0) 2
            else if ((randomNumber - rate[1]!!) < 0) 1
            else 0
        }

        // 从缓存获取天赋数据
        val talentData = JacksonUtil.readTree(redisService.getValue("lifeRestart:talentData").toString())

        val talentList = mutableMapOf<Int, MutableList<TalentDataVo>>()

        for (talent in talentData) {
            val talentId = talent.get("id").asInt().toString()
            val grade = talent.get("grade").intValue()
            val name = talent.get("name").textValue()
            val description = talent.get("description").textValue()
            // 天赋排除条件
            val exclude = if (talent.has("exclude")) {
                if (talent.get("exclude").isArray) talent.get("exclude").map { it.asText() }
                else listOf()
            } else listOf()

            // 天赋影响的属性
            val effect: Map<String, Int> =
                if (talent.has("effect")) JacksonUtil.jsonNodeToMap(talent.get("effect")) else mapOf()

            // 天赋发动的条件
            val condition = if (talent.has("condition")) talent.get("condition").textValue() else null

            // 被替换的天赋条件
            val replacement: Map<String, List<Any>> =
                if (talent.has("replacement")) JacksonUtil.jsonNodeToMap(talent.get("replacement")) else mapOf()

            val exclusive = talent.get("exclusive")?.booleanValue() ?: true
            if (!exclusive) continue

            val talentDataVo = TalentDataVo(
                id = talentId,
                grade = grade,
                name = name,
                description = description,
                exclude = exclude,
                effect = effect,
                condition = condition,
                replacement = replacement
            )
            if (talentList[grade] == null) talentList[grade] = mutableListOf(talentDataVo)
            else talentList[grade]?.add(talentDataVo)
        }

        val result = mutableListOf<TalentDataVo>()
        repeat(talentConfig.talentPullCount) { i ->
            if (i == 0 && include != null) {
                result.add(include)
            } else {
                var grade = randomGrade()
                while (talentList[grade]?.isEmpty() == true) grade--
                val length = talentList[grade]?.size ?: 0
                val random = (Math.random() * length).toInt() % length
                talentList[grade]?.removeAt(random)?.let { result.add(it) }
            }
        }
        return result
    }

    /**
     * 获取天赋概率加成
     *
     * @param type 类型
     * @param value 值
     * @return 天赋概率加成
     */
    private fun getAddition(type: String, value: Int): MutableMap<Int, Int>? {
        talentConfig.additions[type]?.forEach { mapList ->
            mapList.forEach { (min, addition) ->
                if (value >= min) return addition
            }
        }
        return null
    }

    /**
     * 获取天赋概率
     *
     * @param userInfo 用户信息
     * @return 天赋概率
     */
    private fun getRate(userInfo: UserInfoEntity): MutableMap<Any, Int> {
        val rate = talentConfig.talentRate.toMutableMap()
        val addition = mutableMapOf(1 to 1, 2 to 1, 3 to 1)

        val additionValues = mutableMapOf("TMS" to userInfo.gameTimes, "CACHV" to userInfo.achievement)

        additionValues.forEach { (key, value) ->
            val addi = getAddition(key, value)
            addi?.forEach { (grade, _) ->
                addition[grade] = addition[grade]!! + addi[grade]!!
            }
        }

        addition.forEach { (grade, _) ->
            rate[grade] = rate[grade]!! * addition[grade]!!
        }

        return rate
    }

    /**
     * 选择天赋
     *
     * @param match 匹配到的天赋值
     * @param userInfo 用户信息
     */
    fun getChoiceTalent(match: String, userInfo: UserInfoEntity) {
        val talentIdList = mutableListOf<String>()
        val matchList = match.split(" ")
        matchList.forEach { index ->
            userInfo.randomTalentTemp[index.toInt() - 1].let { talentDataVo ->
                userInfo.talent.add(talentDataVo)
                talentIdList.add(talentDataVo.id!!)
            }
        }
    }

    /**
     * 天赋选择检查
     *
     * @param match 匹配项
     * @param userInfo 用户信息
     * @return 检查结果
     */
    fun talentCheck(match: String, userInfo: UserInfoEntity): String {
        var matchList = match.split(" ")
        matchList = matchList.distinct()
        return if (matchList.size < userInfo.talentSelectLimit) {
            TALENT_SELECT_NOT_COMPLETE
        } else if (matchList.size > userInfo.talentSelectLimit) {
            TALENT_SELECT_Limit
        } else {
            TALENT_SELECT_COMPLETE
        }
    }

    fun launchTalent(userInfo: UserInfoEntity): MutableList<TalentDataVo> {
        // 天赋已经全部激活，直接返回空
        if (userInfo.talent.isEmpty()) return mutableListOf()
        // 存储已经发动过的天赋
        val activatedTalents = mutableListOf<TalentDataVo>()
        // 使用迭代器来遍历并移除符合条件的 talent
        val iterator = userInfo.talent.iterator()
        while (iterator.hasNext()) {
            val talent = iterator.next()
            if (talent.condition != null && checkCondition(
                    userInfo = userInfo,
                    condition = talent.condition
                )
            ) {
                listOf(CHR, INT, STR, MNY, SPR).forEach { key ->
                    userInfo.property[key] = userInfo.property[key]?.plus(talent.effect[key] ?: 0) ?: 0
                }
                activatedTalents.add(talent)
                iterator.remove()  // 移除当前 talent
            }
        }
        updateUserInfo(userInfo)
        return activatedTalents
    }


    /**
     *  获取天赋加成，分配初始属性
     *
     * @param userInfo 用户信息
     */
    fun getTalentAllocationAddition(userInfo: UserInfoEntity) {
        val talents = userInfo.talent

        // 初始化无条件天赋的属性
        talents.forEach { talent ->
            if (talent.condition == null && talent.effect.isNotEmpty()) {
                listOf(CHR, INT, STR, MNY, SPR).forEach { key ->
                    if (talent.effect[key] != null) userInfo.property[key] = talent.effect[key]!!
                }
                if (talent.effect[RDM] != null) userInfo.status += talent.effect[RDM]!!
            }
        }
        // 选择天赋后先进行一次判断，符合条件的直接改变属性
    }


    /**
     * 天赋格式正则
     *
     * @param talent 天赋字符串
     * @return Boolean
     */
    fun isTalentFormatValid(talent: String): Boolean {
        return Regex("""^(?:[1-9]|10)(?:\s(?:[1-9]|10))*$""").matches(talent)
    }


    enum class OperationType {
        CHOOSE_TALENT, ALREADY_CHOSE_TALENT, UNALLOCATED, ASSIGNED, CONTINUE
    }

    /**
     * 判断游戏状态
     *
     * @param userInfo 用户信息
     * @param operation 类型
     * @return
     */
    fun errorState(userInfo: UserInfoEntity?, operation: OperationType): RespBean {
        if (userInfo == null) return RespBean.error(RestartRespEnum.GAME_NOT_START)

        fun checkTalentAssigned(): RespBean {
            return when {
                userInfo.talent.isEmpty() -> RespBean.error(RestartRespEnum.TALENT_NOT_CHOSE)
                !userInfo.propertyDistribution -> RespBean.error(RestartRespEnum.NO_ASSIGN_ATTRIBUTES)
                else -> RespBean.success()
            }
        }

        return when (operation) {
            OperationType.CHOOSE_TALENT -> {
                if (userInfo.talent.isNotEmpty())
                    RespBean.error(RestartRespEnum.ALL_READY_CHOSE_TALENT)
                else
                    RespBean.success()
            }

            OperationType.ALREADY_CHOSE_TALENT -> {
                if (userInfo.talent.isEmpty())
                    RespBean.error(RestartRespEnum.TALENT_NOT_CHOSE)
                else
                    RespBean.success()
            }

            OperationType.UNALLOCATED -> {
                if (userInfo.talent.isEmpty())
                    RespBean.error(RestartRespEnum.TALENT_NOT_CHOSE)
                else if (userInfo.propertyDistribution)
                    RespBean.error(RestartRespEnum.ASSIGNED_ATTRIBUTES)
                else
                    RespBean.success()
            }

            OperationType.ASSIGNED -> {
                if (!userInfo.propertyDistribution)
                    RespBean.error(RestartRespEnum.NO_ASSIGN_ATTRIBUTES)
                else
                    RespBean.success()
            }

            OperationType.CONTINUE -> checkTalentAssigned()
        }
    }


    /**
     * 更新时间并发送图片
     *
     * @param userInfo 用户信息
     * @param message 待发送的消息
     */
    fun updateAndSend(
        context: BotUtils.Context,
        userInfo: UserInfoEntity,
        realId: String,
        message: String? = null
    ) {
        val imageData = WebImgUtil.ImgData(
            imgName = "${realId}-LifeStart-${UUID.randomUUID()}",
            url = "http://${webImgUtil.frontendAddress}/game/lifeRestart?game_userId=${realId}"
        )

        webImgUtil.sendNewImage(context, imageData)
        if (message != null) context.sendMsg(message)
        webImgUtil.deleteImg(imageData)

        // 更新用户缓存时间
        redisService.setValueWithExpiry("lifeRestart:userInfo:${realId}", userInfo, 5L, TimeUnit.MINUTES)
    }


    /**
     * 查找与输入值匹配的判定
     *
     * @param input 输入
     * @param judgeList 列表
     * @return 符合的判定
     */
    fun findJudgeForValue(
        input: Int,
        judgeList: List<JudgeItemVO>
    ): JudgeItemVO? {
        // 检查列表是否为空
        if (judgeList.isEmpty()) {
            return null
        }

        for (i in judgeList.indices) {
            val current = judgeList[i]
            val currentMin = current.min ?: continue

            // 处理负数输入的情况
            if (input < 0) {
                // 如果第一个项的 min 也是负数或 0，直接返回
                if (i == 0 && currentMin <= 0) {
                    return current
                }
                // 如果不是第一个项，直接跳过
                continue
            }

            if (i == judgeList.size - 1) {
                if (input >= currentMin) {
                    return current
                }
            } else {
                val next = judgeList[i + 1]
                val nextMin = next.min ?: continue

                if (input in currentMin..<nextMin) {
                    return current
                }
            }
        }

        return null
    }

    /**
     * 每条总评
     *
     * @property value 值
     * @property desc 评价
     */
    data class Evaluate(
        val value: Int? = 0,
        val desc: String? = null,
        val grade: Int = 0
    )

    /**
     * 发送游戏结束信息
     *
     * @param userInfo 用户信息
     */
    fun sendGameEnd(context: BotUtils.Context, userInfo: UserInfoEntity) {
        val imageData = WebImgUtil.ImgData(
            imgName = "${userInfo.userId}-LifeStart-${UUID.randomUUID()}",
            url = "http://${webImgUtil.frontendAddress}/game/lifeRestart?game_userId=${userInfo.userId}"
        )
        webImgUtil.sendNewImage(context, imageData)

        val mapper = jacksonObjectMapper()
        val rootNode: JsonNode = mapper.readValue(File(GRADE_JSONPATH))

        val grade = rootNode["grade"]

        val evaluateMap = mutableMapOf<String, Evaluate>()
        listOf(CHR, INT, STR, MNY, SPR, AGE, SUM).forEach { key ->
            val judgeChrNode: JsonNode? = rootNode.path("judge").path(key)

            // 将特定部分转换为 List<JudgeItemVO>
            val propertyList: List<JudgeItemVO> = mapper.readValue(judgeChrNode.toString())

            evaluateMap[key] = generateEvaluate(key, userInfo, propertyList, grade)
        }

        redisService.setValueWithExpiry("lifeRestart:endGame:${userInfo.userId}", evaluateMap, 5L, TimeUnit.SECONDS)

        val gameOverImgData = WebImgUtil.ImgData(
            imgName = "${userInfo.userId}-LifeStart-${UUID.randomUUID()}",
            url = "http://${webImgUtil.frontendAddress}/game/lifeRestartEndGame?game_userId=${userInfo.userId}"
        )
        webImgUtil.sendNewImage(context, gameOverImgData)

        webImgUtil.deleteImg(imageData)
    }


    private fun generateEvaluate(
        key: String,
        userInfo: UserInfoEntity,
        propertyList: List<JudgeItemVO>,
        rootNode: JsonNode
    ): Evaluate {
        val maxPropertyValue = userInfo.maxProperty[key] ?: if (key != AGE) return Evaluate(0, "") else 0

        val getProperty = findJudgeForValue(maxPropertyValue, propertyList)!!
        val judgeValue = getProperty.judge!!

        return when (key) {
            CHR, MNY, SUM -> Evaluate(
                value = maxPropertyValue,
                desc = rootNode["judge_level"][judgeValue.toString()].textValue(),
                grade = getProperty.grade!!
            )

            INT -> {
                val desc = if (judgeValue < 7) {
                    rootNode["judge_level"][judgeValue.toString()].textValue()
                } else {
                    rootNode["intelligence_judge"][judgeValue.toString()].textValue()
                }
                Evaluate(value = maxPropertyValue, desc = desc, grade = getProperty.grade!!)
            }

            STR -> {
                val desc = if (judgeValue < 7) {
                    rootNode["judge_level"][judgeValue.toString()].textValue()
                } else {
                    rootNode["strength_judge"][judgeValue.toString()].textValue()
                }
                Evaluate(value = maxPropertyValue, desc = desc, grade = getProperty.grade!!)
            }

            SPR -> Evaluate(
                value = maxPropertyValue,
                desc = rootNode["judge_level_color"][judgeValue.toString()].textValue(), grade = getProperty.grade!!
            )

            AGE -> Evaluate(
                value = userInfo.age,
                desc = rootNode["age_judge"][(findJudgeForValue(
                    userInfo.age,
                    propertyList
                )!!.judge!!).toString()].textValue(),
                grade = getProperty.grade!!
            )

            else -> Evaluate(0, "", 0)
        }
    }


}