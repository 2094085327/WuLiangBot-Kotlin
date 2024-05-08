package bot.demo.txbot.game.lifeRestart

import bot.demo.txbot.common.utils.ExcelReader
import bot.demo.txbot.common.utils.JacksonUtil
import com.fasterxml.jackson.databind.JsonNode
import java.io.File
import java.util.logging.Logger
import java.util.regex.Matcher
import kotlin.random.Random


/**
 * @description: 人生重开用到的方法
 * @author Nature Zero
 * @date 2024/2/16 13:24
 */
class LifeRestartUtil {
    private val logger: Logger = Logger.getLogger(ExcelReader::class.java.getName()) // 日志打印类

    data class UserInfo(
        val userId: String,
        var attributes: Map<*, *>? = null,
        var age: Int,
        var events: MutableList<Any> = mutableListOf(),
        var property: MutableMap<String, Any>? = null,
        var propertyDistribution: Boolean? = false,
        var talent: MutableList<Any> = mutableListOf(),
        var isEnd: Boolean? = false,
        var gameTimes: Int = 0,
        var achievement: Int = 0,
        var randomTalentTemp: MutableList<Any>? = mutableListOf(),
        var status: Int = 20,
        var activeGameTime: Long = System.currentTimeMillis()
    )

    var ageData: Any? = null
    var eventData: Any? = null
    private var talentData: Any? = null

    /**
     * 获取并更新数据
     *
     * @return 判断是否有文件缺失
     */
    fun fetchDataAndUpdateLists(): Boolean {

        fun readAgeData(): Any? {
            return if (File(AGE_JSONPATH).exists()) {
                JacksonUtil.getJsonNode(AGE_JSONPATH)
            } else {
                logger.warning(AGE_JSON_MISS)
                ExcelReader().readExcel(AGE_EXCEL_PATH, TYPE_AGE)
            }
        }

        fun readEventData(): Any? {
            return if (File(EVENT_JSONPATH).exists()) {
                JacksonUtil.getJsonNode(EVENT_JSONPATH)
            } else {
                logger.warning(EVENT_JSON_MISS)
                ExcelReader().readExcel(EVENT_EXCEL_PATH, TYPE_EVENT)
            }
        }

        fun readTalentData(): Any? {
            return if (File(EVENT_JSONPATH).exists()) {
                JacksonUtil.getJsonNode(TALENT_JSONPATH)
            } else {
                logger.warning(TALENT_JSON_MISS)
                ExcelReader().readExcel(TALENT_EXCEL_PATH, TYPE_TALENT)
            }
        }

        val ageData = readAgeData()
        val eventData = readEventData()
        val talentData = readTalentData()

        if (ageData == null && eventData == null && talentData == null) return false
        if (ageData == null) return false
        if (eventData == null) return false
        if (talentData == null) return false


        // 更新列表数据
        this.ageData = ageData
        this.eventData = eventData
        this.talentData = talentData
        return true
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
        userInfo: UserInfo,
        attributeNames: List<String>,
        mutableProperty: MutableMap<String, Any>
    ): MutableMap<String, Any>? {
        mutableProperty[EVT] = mutableListOf<String>()
        mutableProperty[LIF] = 1.plus((userInfo.property!![LIF] ?: 0) as Int)
        mutableProperty[SPR] = 5.plus((userInfo.property!![SPR] ?: 0) as Int)
        if (userInfo.property!![RDM] != null) {
            val randomAttributes = attributeNames.random()
            mutableProperty[randomAttributes] =
                (mutableProperty[randomAttributes] as Int).plus(userInfo.property!![RDM] as Int)
            return mutableProperty
        }
        return null
    }

    /**
     * 随机生成属性
     *
     * @param userInfo 用户信息
     * @return 属性
     */
    fun randomAttributes(userInfo: UserInfo): MutableMap<String, Any> {
        // 如果property为null，初始化为一个新的MutableMap
        if (userInfo.property == null) {
            userInfo.property = mutableMapOf()
        }

        var mutableProperty = userInfo.property as MutableMap<String, Any>

        // 随机选择第一个字段，给定0-10之间的随机值
        val attributeNames = listOf(CHR, INT, STR, MNY)
        val firstAttributeName = attributeNames.shuffled().first()
        val firstValue = (0..10).random()
        mutableProperty[firstAttributeName] = firstValue

        // 随机选择剩下的字段，给定0到（20-第一个值）之间的随机值
        val remainingAttributes = attributeNames - firstAttributeName
        var remainingSum = userInfo.status - firstValue

        for (attributeName in remainingAttributes.shuffled()) {
            val maxPossibleValue = minOf(remainingSum, 10) // 确保属性最大值为10
            val value = if (remainingAttributes.last() == attributeName) maxPossibleValue
            else (0..maxPossibleValue).random()

            mutableProperty[attributeName] = (mutableProperty[attributeName] as Int).plus(value)
            remainingSum -= value
        }

        mutableProperty = additionalAttributes(userInfo, attributeNames, mutableProperty) ?: mutableProperty
        return mutableProperty
    }

    /**
     * 手动分配属性
     *
     * @param match 匹配
     * @return 属性
     */
    fun assignAttributes(userInfo: UserInfo, match: Matcher): Any {
        val attributeValues = match.group(1).split(" ").map(String::toInt)
        val total = attributeValues.sum()
        if (total > userInfo.status) return SIZE_OUT
        for (value in attributeValues) if (value > 10) return VALUE_OUT

        // 如果property为null，初始化为一个新的MutableMap
        userInfo.property = userInfo.property ?: mutableMapOf()

        var mutableProperty = userInfo.property as MutableMap<String, Any>
        val attributeNames = listOf(CHR, INT, STR, MNY)

        attributeNames.forEachIndexed { index, attributeName ->
            if (mutableProperty[attributeName] != null) {
                mutableProperty[attributeName] = (mutableProperty[attributeName] as Int).plus(attributeValues[index])
            } else {
                mutableProperty[attributeName] = attributeValues[index]
            }
        }
        mutableProperty = additionalAttributes(userInfo, attributeNames, mutableProperty) ?: mutableProperty
        userInfo.property = mutableProperty
        return true
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
     * @param property 属性
     * @param condition 条件
     * @return 是否满足条件
     */
    private fun checkProp(property: Map<String, Any>, condition: String): Boolean {
        val length = condition.length
        var i = condition.indexOfFirst { it == '>' || it == '<' || it == '!' || it == '?' || it == '=' }

        // 要求的属性
        val prop = condition.substring(0, i)
        val symbol = condition.substring(i, if (condition[i + 1] == '=') i++ + 2 else i++ + 1)
        val d = condition.substring(i, length)

        val propData = property[prop]

        // 如果是列表，就转换成列表，否则转换成整数
        val conditionData: Any =
            if (d.startsWith("[")) d.substring(1, d.length - 1).split(",").map { it.trim() } else d.toInt()

        return when (symbol) {
            ">" -> (propData as Int) > conditionData as Int
            "<" -> (propData as Int) < conditionData as Int
            ">=" -> propData as Int >= conditionData as Int
            "<=" -> propData as Int <= conditionData as Int
            "=" -> if (propData is List<*>) propData.contains(conditionData) else propData == conditionData
            "!=" -> if (propData is List<*>) !propData.contains(conditionData) else propData != conditionData
            "?" -> if (propData is List<*>) propData.any { it in conditionData as List<*> } else conditionData is List<*> && propData in conditionData
            "!" -> if (propData is List<*>) propData.none { it in conditionData as List<*> } else conditionData is List<*> && propData !in conditionData
            else -> false
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
     * @param property 属性
     * @param conditions 条件
     * @return 是否满足条件
     */
    private fun checkParsedConditions(property: Map<String, Any>, conditions: Any): Boolean {
        if (conditions !is List<*>) {
            conditions as String
            return checkProp(property, conditions)
        }
        if (conditions.isEmpty())
            return true
        if (conditions.size == 1)
            return checkParsedConditions(property, conditions[0]!!)

        var ret = checkParsedConditions(property, conditions[0]!!)
        for (i in 1 until conditions.size step 2) {
            when (conditions[i] as String) {
                "&" ->
                    if (ret)
                        ret = checkParsedConditions(property, conditions[i + 1]!!)

                "|" ->
                    if (ret)
                        return true
                    else
                        ret = checkParsedConditions(property, conditions[i + 1]!!)

                else -> return false
            }
        }

        return ret
    }

    /**
     * 检查条件
     *
     * @param property 所有物
     * @param condition 条件
     * @return
     */
    private fun checkCondition(property: Map<String, Any>, condition: String): Boolean {
        val parsedCondition = parseCondition(condition)
        return checkParsedConditions(property, parsedCondition)
    }

    /**
     * 找出当前年龄下所有符合条件的事件
     *
     * @param userInfo 用户信息
     * @param ageDataVO 年龄列表
     * @return 符合条件的事件列表
     */
    private fun generateValidEvent(userInfo: UserInfo, ageDataVO: AgeDataVO): List<Map<String, Double>> {
        return ageDataVO.eventList?.mapNotNull { event ->
            event?.let {
                val splitEvent = it.toString().split("*").map(String::toDouble)
                val eventId = splitEvent[0].toInt().toString()
                val weight = if (splitEvent.size == 1) 1.0 else splitEvent[1]
                if (eventCheck(userInfo = userInfo, eventId = eventId)) {
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
    private fun getRandom(userInfo: UserInfo): String {
        var ageDataVO = AgeDataVO()

        when (ageData) {
            is JsonNode -> {
                val ageList = (ageData as JsonNode).get(userInfo.age.toString())
                val age: Int = userInfo.age
                val eventList: MutableList<Any?> = mutableListOf()
                for (element in ageList[TYPE_EVENT]) eventList.add(element.textValue())

                ageDataVO = AgeDataVO(age = age, eventList = eventList)
            }

            is MutableList<*> -> {
                ageDataVO = (ageData as MutableList<*>).find {
                    it as AgeDataVO
                    it.age == userInfo.age
                } as AgeDataVO
            }
        }

        val eventListCheck = generateValidEvent(userInfo, ageDataVO)
        return weightRandom(eventListCheck)
    }

    /**
     * 判断游戏是否结束（生命值小于1）
     *
     * @param userInfo 用户信息
     * @return 是否结束
     */
    private fun isEnd(userInfo: UserInfo) {
        val lif = userInfo.property?.get(LIF) as Int
        if (lif < 1) userInfo.isEnd = true
    }

    /**
     * 下一岁年龄
     *
     * @param userInfo 用户信息
     */
    private fun ageNext(userInfo: UserInfo) {
        userInfo.age += 1
    }

    /**
     * 获取事件
     *
     * @param userInfo 用户信息
     * @param eventId 事件ID
     * @return 事件
     */
    private fun getDo(userInfo: UserInfo, eventId: String): List<Any?> {
        var branchItem: String? = null
        var event: EventDataVO? = null

        when (val eventData = eventData) {
            is JsonNode -> {
                val eventDataJson = eventData.get(eventId)
                if (eventDataJson != null) {
                    val eventEffect = eventDataJson["effect"]
                    event = EventDataVO(
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
                    branchItem = eventDataJson["branch"]?.filterNotNull()?.firstOrNull { branch ->
                        val cond = branch.textValue().split(":").firstOrNull()
                        cond?.let { thisCond -> checkCondition(userInfo.property ?: emptyMap(), thisCond) } == true
                    }?.textValue()
                }
            }

            is MutableList<*> -> {
                event = eventData.filterIsInstance<EventDataVO>().firstOrNull { it.id == eventId }
                branchItem = event?.branch?.filterNotNull()?.firstOrNull { branch ->
                    val cond = branch.split(":").firstOrNull()
                    cond?.let { thisCond -> checkCondition(userInfo.property ?: emptyMap(), thisCond) } == true
                }
            }
        }

        return if (branchItem != null) {
            listOf(event) + branchItem.split(":").getOrNull(1)
        } else {
            listOf(event)
        }
    }


    /**
     * 执行事件
     *
     * @param userInfo
     * @param eventId
     * @return 事件内容
     */
    @Suppress("UNCHECKED_CAST")
    private fun doEvent(userInfo: UserInfo, eventId: String): List<Any?> {
        val eventData = getDo(userInfo, eventId)
        val eventSize = eventData.size
        val event = eventData[0] as EventDataVO
        val property = userInfo.property as MutableMap<String, Any>
        val effectStr = StringBuilder()

        listOf(CHR, INT, STR, MNY, SPR).forEach { key ->
            val value = when (key) {
                CHR -> event.effectChr
                INT -> event.effectInt
                STR -> event.effectStr
                MNY -> event.effectMny
                SPR -> event.effectSpr
                else -> null
            }
            value?.let {
                property[key] = (property[key] as Int?)?.plus(it) ?: it
                when (key) {
                    CHR -> effectStr.append("颜值:$value\n")
                    INT -> effectStr.append("智力:$value\n")
                    STR -> effectStr.append("体质:$value\n")
                    MNY -> effectStr.append("家境:$value\n")
                    SPR -> effectStr.append("快乐:$value\n")
                }
            }
        }

        event.effectLif?.let { property[LIF] = (property[LIF] as Int?)?.plus(it) ?: it }
        event.effectAge?.let { userInfo.age += it }
        property[EVT] = (property[EVT] as MutableList<String>) + event.id

        val contentMap = mapOf("event" to event, "effect" to effectStr.toString(), "age" to userInfo.age)

        return if (eventSize == 1) {
            mutableListOf(contentMap)
        } else {
            val getEventId = eventData[1] as String
            mutableListOf(contentMap) + doEvent(userInfo, getEventId)
        }
    }

    /**
     * 下一步
     *
     * @param userInfo 用户信息
     */
    fun next(userInfo: UserInfo): MutableMap<String, Any> {
        ageNext(userInfo)
        val eventContent = doEvent(userInfo, getRandom(userInfo))
        isEnd(userInfo)
        return mutableMapOf("eventContent" to eventContent)
    }

    /**
     * 重开运行
     *
     * @param userInfo 用户信息
     * @return 事件内容
     */
    fun trajectory(userInfo: UserInfo): Any? {
        val trajectory = next(userInfo)
        return trajectory["eventContent"]
    }

    /**
     * 事件检查
     *
     * @param userInfo 用户信息
     * @param eventId 事件ID
     * @return 是否满足条件
     */
    private fun eventCheck(userInfo: UserInfo, eventId: String): Boolean {
        when (eventData) {
            is JsonNode -> {
                val eventList = (eventData as JsonNode).get(eventId)
                if (eventList.get("noRandom") != null) return false
                if (eventList.get("exclude") != null && checkCondition(
                        userInfo.property!!,
                        eventList.get("exclude").textValue()
                    )
                ) return false
                if (eventList.get("include") != null) return checkCondition(
                    userInfo.property!!,
                    eventList.get("include").textValue()
                )
            }

            is MutableList<*> -> {
                val eventList = (eventData as MutableList<*>).filterIsInstance<EventDataVO>()
                eventList.find {
                    it.id == eventId
                }.let {
                    it as EventDataVO
                    if (it.noRandom != null) return false
                    if (it.exclude != null && checkCondition(userInfo.property!!, it.exclude.toString())) return false
                    if (it.include != null) return checkCondition(userInfo.property!!, it.include.toString())
                }
            }
        }

        return true
    }

    private val talentConfig = TalentConfig()

    /**
     * 人生重开天赋配置
     *
     * @param userInfo 用户信息
     * @return 天赋列表
     */
    fun talentRandomInit(userInfo: UserInfo): MutableList<Any> {
//        val lastExtentTalent = lastExtentTalent(userInfo)
        // TODO 上一次保留的天赋
        val lastExtentTalent = null
        val talentRandom = talentRandom(lastExtentTalent, userInfo)
        return talentRandom
    }


    /**
     * 随机天赋
     *
     * @param include 包含的天赋
     * @param userInfo 用户信息
     * @return 天赋列表
     */
    private fun talentRandom(include: Any?, userInfo: UserInfo): MutableList<Any> {
        val rate = getRate(userInfo)

        fun randomGrade(): Int {
            val randomNumber = (0 until rate["total"]!!).random()
            return if ((randomNumber - rate[3]!!) < 0) {
                3
            } else if ((randomNumber - rate[2]!!) < 0) {
                2
            } else if ((randomNumber - rate[1]!!) < 0) {
                1
            } else 0
        }

        val talentList = mutableMapOf<Int, MutableList<TalentDataVo>>()
        when (talentData) {
            is JsonNode -> {
                val talentListJson = talentData as JsonNode
                for (talent in talentListJson) {
                    val talentId = talent.get("id").asInt().toString()
                    val grade = talent.get("grade").intValue()
                    val name = talent.get("name").textValue()
                    val description = talent.get("description").textValue()
                    val exclusive = talent.get("exclusive")?.booleanValue()
                    if (exclusive == true) continue


                    val talentDataVo = TalentDataVo(grade, name, description, talentId)
                    if (talentList[grade] == null) talentList[grade] = mutableListOf(talentDataVo)
                    else talentList[grade]?.add(talentDataVo)
                }
            }

            is MutableList<*> -> {
                val talentList = talentData as MutableList<*>
                // TODO talent Excle
            }
        }

        val result = mutableListOf<Any>()
        repeat(talentConfig.talentPullCount) { i ->
            if (i == 0 && include != null) {
                result.add(include)
            } else {
                var grade = randomGrade()
                while (talentList[grade]?.isEmpty() == true) grade--
                val length = talentList[grade]?.size ?: 0
                val random = (Math.random() * length).toInt() % length
                result.add(talentList[grade]?.removeAt(random) ?: "")
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
    private fun getRate(userInfo: UserInfo): MutableMap<Any, Int> {
        val rate = talentConfig.talentRate
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

    fun lastExtentTalent(userInfo: UserInfo): Any {
        return userInfo.talent.last()
    }

    /**
     * 选择天赋
     *
     * @param match 匹配到的天赋值
     * @param userInfo 用户信息
     */
    fun getChoiceTalent(match: String, userInfo: UserInfo) {
        val talentIdList = mutableListOf<String>()
        match.split(" ").forEach { index ->
            userInfo.randomTalentTemp?.get(index.toInt() - 1)?.let { talentDataVo ->
                talentDataVo as TalentDataVo
                userInfo.talent.add(talentDataVo)
                talentIdList.add(talentDataVo.id)
            }
        }
        userInfo.property = userInfo.property ?: mutableMapOf()
        userInfo.property?.plusAssign(mutableMapOf("TLT" to talentIdList))
    }

    /**
     * 获取天赋加成，分配初始属性
     *
     */
    fun getTalentAllocationAddition(userInfo: UserInfo) {
        val talent: MutableList<String> = userInfo.property?.get("TLT") as MutableList<String>
        val userProperty = userInfo.property as MutableMap<String, Any>
        talent.forEach { talentId ->
            when (talentData) {
                is JsonNode -> {
                    val talentDataJson = talentData as JsonNode
                    val talentData = talentDataJson.get(talentId)
                    val status = talentData.get("status")?.intValue() ?: 0
                    userInfo.status += status
                    val effect = talentData.get("effect") ?: null
                    effect?.let {
                        listOf(CHR, INT, STR, MNY, SPR).forEach { key ->
                            val value = effect.get(key)?.intValue()
                            value?.let {
                                userProperty[key] = (userProperty[key] as Int?)?.plus(value) ?: value
                            }
                        }
                    }
                }

                is MutableList<*> -> {
                    // TODO 表格数据
                }
            }
        }
    }


}