package bot.demo.txbot.game.lifeRestart

import bot.demo.txbot.common.utils.ExcelReader
import bot.demo.txbot.common.utils.JacksonUtil
import com.fasterxml.jackson.databind.JsonNode
import java.io.File
import java.util.regex.Matcher
import kotlin.random.Random


/**
 * @description: 人生重开用到的方法
 * @author Nature Zero
 * @date 2024/2/16 13:24
 */
class LifeRestartUtil {
    data class UserInfo(
        val userId: String,
        var attributes: Map<*, *>? = null,
        var age: Int,
        var events: MutableList<Any> = mutableListOf(),
        var property: Map<String, Any>? = null,
        var isEnd: Boolean? = false
    )

    var ageData: Any? = null
    var eventData: Any? = null

    /**
     * 获取并更新数据
     *
     * @return 判断是否有文件缺失
     */
    fun fetchDataAndUpdateLists(): Boolean {
        val ageJsonPath = "resources/lifeRestart/age.json"
        val eventJsonPath = "resources/lifeRestart/event.json"
        val ageExcelPath = "resources/lifeRestart/age.xlsx"
        val eventExcelPath = "resources/lifeRestart/events.xlsx"

        fun readAgeData(): Any? {
            return if (File(ageJsonPath).exists()) {
                JacksonUtil.getJsonNode(ageJsonPath)
            } else {
                ExcelReader().readExcel(ageExcelPath, "age")
            }
        }

        fun readEventData(): Any? {
            return if (File(eventJsonPath).exists()) {
                JacksonUtil.getJsonNode(eventJsonPath)
            } else {
                ExcelReader().readExcel(eventExcelPath, "event")
            }
        }

        val ageData = readAgeData()
        val eventData = readEventData()

        if (ageData == null && eventData == null) return false
        if (ageData == null) return false
        if (eventData == null) return false


        // 更新列表数据
        this.ageData = ageData
        this.eventData = eventData
        return true
    }


    /**
     * 随机生成属性
     *
     */
    fun randomAttributes(userInfo: UserInfo): Map<String, Any> {
        // 如果property为null，初始化为一个新的MutableMap
        if (userInfo.property == null) {
            userInfo.property = mutableMapOf()
        }

        val mutableProperty = userInfo.property as MutableMap<String, Any>

        // 随机选择第一个字段，给定0-10之间的随机值
        val attributeNames = listOf("CHR", "INT", "STR", "MNY")
        val firstAttributeName = attributeNames.shuffled().first()
        val firstValue = (0..10).random()
        mutableProperty[firstAttributeName] = firstValue

        // 随机选择剩下的字段，给定0到（10-第一个值）之间的随机值
        val remainingAttributes = attributeNames - firstAttributeName
        var remainingSum = 20 - firstValue

        for (attributeName in remainingAttributes.shuffled()) {
            val maxPossibleValue = minOf(remainingSum, 10) // 确保属性最大值为10
            val value = if (remainingAttributes.last() == attributeName) maxPossibleValue
            else (0..maxPossibleValue).random()

            mutableProperty[attributeName] = value
            remainingSum -= value
        }
        mutableProperty["EVT"] = mutableListOf<String>()
        mutableProperty["LIF"] = 1
        mutableProperty["SPR"] = 5

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
        if (total > 20) return "sizeOut"
        for (value in attributeValues) {
            if (value > 10) return "valueOut"
        }

        // 如果property为null，初始化为一个新的MutableMap
        userInfo.property = userInfo.property ?: mutableMapOf()

        val mutableProperty = userInfo.property as MutableMap<String, Any>
        val attributeNames = listOf("CHR", "INT", "STR", "MNY")

        attributeNames.forEachIndexed { index, attributeName ->
            mutableProperty[attributeName] = attributeValues[index]
        }
        mutableProperty["EVT"] = mutableListOf<String>()
        mutableProperty["LIF"] = 1
        mutableProperty["SPR"] = 5

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
                for (element in ageList["event"]) eventList.add(element.textValue())

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
        val lif = userInfo.property?.get("LIF") as Int
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
                    event = EventDataVO(
                        id = eventId,
                        event = eventDataJson["event"].textValue(),
                        grade = eventDataJson["grade"].intValue(),
                        postEvent = eventDataJson["postEvent"]?.textValue(),
                        effectChr = eventDataJson["effect"]["CHR"].intValue(),
                        effectInt = eventDataJson["effect"]["INT"].intValue(),
                        effectStr = eventDataJson["effect"]["STR"].intValue(),
                        effectMny = eventDataJson["effect"]["MNY"].intValue(),
                        effectSpr = eventDataJson["effect"]["SPR"].intValue(),
                        effectLif = eventDataJson["effect"]["LIF"].intValue(),
                        effectAge = eventDataJson["effect"]["AGE"].intValue(),
                        noRandom = eventDataJson["NoRandom"]?.intValue(),
                        include = eventDataJson["include"]?.textValue(),
                        exclude = eventDataJson["exclude"]?.textValue(),
                        branch = eventDataJson["branch"]?.mapNotNull { it?.textValue() }?.toMutableList()
                    )
                    branchItem = eventDataJson["branch"]?.filterNotNull()?.firstOrNull { branch ->
                        val cond = branch.textValue().split(":").firstOrNull()
                        cond?.let { thisCond -> checkCondition(userInfo.property ?: emptyMap(), thisCond) } == true
                    }?.toString()
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

        listOf("CHR", "INT", "STR", "MNY", "SPR").forEach { key ->
            val value = when (key) {
                "CHR" -> event.effectChr
                "INT" -> event.effectInt
                "STR" -> event.effectStr
                "MNY" -> event.effectMny
                "SPR" -> event.effectSpr
                else -> null
            }
            value?.let {
                property[key] = (property[key] as Int?)?.plus(it) ?: it
                when (key) {
                    "CHR" -> effectStr.append("颜值:$value\n")
                    "INT" -> effectStr.append("智力:$value\n")
                    "STR" -> effectStr.append("体质:$value\n")
                    "MNY" -> effectStr.append("家境:$value\n")
                    "SPR" -> effectStr.append("快乐:$value\n")
                }
            }
        }

        event.effectLif?.let { property["LIF"] = (property["LIF"] as Int?)?.plus(it) ?: it }
        event.effectAge?.let { userInfo.age += it }
        property["EVT"] = (property["EVT"] as MutableList<String>) + event.id

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
        when(eventData){
            is JsonNode -> {
                val eventList = (eventData as JsonNode).get(eventId)
                if (eventList.get("noRandom") != null) return false
                if (eventList.get("exclude") != null && checkCondition(userInfo.property!!, eventList.get("exclude").toString())) return false
                if (eventList.get("include") != null) return checkCondition(userInfo.property!!, eventList.get("include").toString())
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
}