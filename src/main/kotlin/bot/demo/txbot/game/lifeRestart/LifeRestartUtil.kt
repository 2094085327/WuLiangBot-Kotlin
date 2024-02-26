package bot.demo.txbot.game.lifeRestart

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

    var eventList: MutableList<Any> = mutableListOf()
    var ageList: MutableList<Any> = mutableListOf()


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
        val attributeValues = match.group(1).split(" ").map { it.toInt() }
        val total = attributeValues.sum()
        if (total > 20) return "sizeOut"

        // 如果property为null，初始化为一个新的MutableMap
        if (userInfo.property == null) {
            userInfo.property = mutableMapOf()
        }

        val mutableProperty = userInfo.property as MutableMap<String, Any>
        val attributeNames = listOf("CHR", "INT", "STR", "MNY")

        for (attributeName in attributeNames) {
            mutableProperty[attributeName] = attributeValues[attributeNames.indexOf(attributeName)]
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
    fun checkCondition(property: Map<String, Any>, condition: String): Boolean {
        val parsedCondition = parseCondition(condition)
        return checkParsedConditions(property, parsedCondition)
    }

    /**
     * 事件初始化
     *
     * @param userInfo 用户信息
     */
    @Suppress("UNCHECKED_CAST")
    fun eventInitial(userInfo: UserInfo): String {
        val eventId = getRandom(userInfo)

        (userInfo.property!!["EVT"] as MutableList<String>).add(eventId)
        return eventId
    }

    /**
     * 找出当前年龄下所有符合条件的事件
     *
     * @param userInfo 用户信息
     * @param ageList 年龄列表
     * @return 符合条件的事件列表
     */
    private fun generateValidEvent(userInfo: UserInfo, ageList: AgeDataVO): List<Map<String, Double>> {
        return ageList.eventList?.mapNotNull { event ->
            event?.let {
                val splitEvent = it.split("*").map(String::toDouble)
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
        val ageList = ageList.find {
            it as AgeDataVO
            it.age == userInfo.age
        } as AgeDataVO

        val eventListCheck = generateValidEvent(userInfo, ageList)
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
        val eventData = eventList.firstOrNull { it is EventDataVO && it.id == eventId } as? EventDataVO
        val branchItem = eventData?.branch?.filterNotNull()?.firstOrNull { branchItem ->
            val cond = branchItem.split(":").getOrNull(0)
            cond?.let { checkCondition(userInfo.property ?: emptyMap(), it) } == true
        }
        return if (branchItem != null) {
//            listOf(eventData) + doEvent(userInfo, branchItem.split(":")[1])
            listOf(eventData) + branchItem.split(":")[1]

        } else {
            listOf(eventData)
        }
    }

    /**
     * 执行事件
     *
     * @param userInfo
     * @param eventId
     * @return 事件内容
     */
    private fun doEvent(userInfo: UserInfo, eventId: String): List<Any?> {
        val eventData = getDo(userInfo, eventId)
        val eventSize = eventData.size
        val event = eventData[0] as EventDataVO
        val property = userInfo.property as MutableMap<String, Any>
        var effectStr = ""

        val effects = mapOf(
            "CHR" to event.effectChr,
            "INT" to event.effectInt,
            "STR" to event.effectStr,
            "MNY" to event.effectMny,
            "SPR" to event.effectSpr
        )
        println("property: $property")
        println("effects: $effects")
        effects.forEach { (key, value) ->
            value?.let {
//                effectStr += "$key:$value "
                property[key] = (property[key] as Int) + value
                when (key) {
                    "CHR" -> effectStr += "颜值:$value\n"
                    "INT" -> effectStr += "智力:$value\n"
                    "STR" -> effectStr += "体质:$value\n"
                    "MNY" -> effectStr += "家境:$value\n"
                    "SPR" -> effectStr += "快乐:$value\n"
                }
            }
        }

        event.effectLif?.let { property["LIF"] = (property["LIF"] as Int) + it }
        event.effectAge?.let { userInfo.age += it }
        property["EVT"] = (property["EVT"] as MutableList<String>) + event.id

        val contentMap = mapOf("event" to event, "effect" to effectStr, "age" to userInfo.age)


        return if (eventSize == 1) {
            println("contentMap1: $contentMap")

            mutableListOf(contentMap)
        } else {
            println("contentMap2: $contentMap")
            println("eventData: $eventData")

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

    fun trajectory(userInfo: UserInfo): Any? {
        val trajectory = next(userInfo)
//        val eventContent = trajectory["eventContent"] as List<Map<String, Any?>>
        val eventContent = trajectory["eventContent"]
        return eventContent
//        return "${userInfo.age}岁: ${
//            eventContent.joinToString("\n\t") { eventContentMap ->
//                val event = eventContentMap["event"] as EventDataVO
//                event.event + (event.postEvent?.let { "\n\t$it" } ?: "")
//            }
//        }${eventContent.mapNotNull { if (it["effect"] != "") "\n\t${it["effect"]}" else null }.joinToString()}"
    }

    /**
     * 事件检查
     *
     * @param userInfo 用户信息
     * @param eventId 事件ID
     * @return 是否满足条件
     */
    private fun eventCheck(userInfo: UserInfo, eventId: String): Boolean {


        eventList.find {
            it as EventDataVO
            it.id == eventId
        }.let {
            it as EventDataVO
            if (it.noRandom != null) return false
            if (it.exclude != null && checkCondition(userInfo.property!!, it.exclude.toString())) return false
            if (it.include != null) return checkCondition(userInfo.property!!, it.include.toString())

            println("eventPrp: ${userInfo.property}")
            println("eventIt: $it")
        }
        return true
    }
}