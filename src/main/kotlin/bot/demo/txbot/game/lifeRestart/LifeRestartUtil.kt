package bot.demo.txbot.game.lifeRestart

import java.util.regex.Matcher


/**
 * @description: 人生重开用到的方法
 * @author Nature Zero
 * @date 2024/2/16 13:24
 */
class LifeRestartUtil {
    data class UserInfo(
        val userId: String,
        var attributes: Map<*, *>? = null,
        val age: Int,
        val events: List<EventDataVO>? = null,
    )


    /**
     * 随机生成属性
     *
     */
    fun randomAttributes(): Map<String, Int> {
        val attributesMap = mutableMapOf<String, Int>()

        // 随机选择第一个字段，给定0-10之间的随机值
        val attributeNames = listOf("颜值", "智力", "体质", "家境", "快乐")
        val firstAttributeName = attributeNames.shuffled().first()
        val firstValue = (0..10).random()
        attributesMap[firstAttributeName] = firstValue

        // 随机选择剩下的字段，给定0到（10-第一个值）之间的随机值
        val remainingAttributes = attributeNames - firstAttributeName
        var remainingSum = 10 - firstValue

        for (attributeName in remainingAttributes.shuffled()) {
            val value = if (remainingAttributes.last() == attributeName) remainingSum
            else (0..remainingSum).random()

            attributesMap[attributeName] = value
            remainingSum -= value
        }

        return attributesMap
    }

    data class Attributes(
        val charisma: Int,
        val intelligence: Int,
        val strength: Int,
        val money: Int,
        val happiness: Int
    )

    fun assignAttributes(match: Matcher): Any {
        val attributeValues = match.group(1).split(" ").map { it.toInt() }
        val attributes = Attributes(
            charisma = attributeValues[0],
            intelligence = attributeValues[1],
            strength = attributeValues[2],
            money = attributeValues[3],
            happiness = attributeValues[4]
        )

        val total =
            attributes.charisma + attributes.intelligence + attributes.strength + attributes.money + attributes.happiness
        if (total > 10) {
            return "sizeOut"
        }

        val attributeNames = listOf("颜值", "智力", "体质", "家境", "快乐")
        return attributeNames.zip(attributeValues).toMap()
    }

    /**
     * 使用游戏购买额外属性
     * TODO
     */
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


    fun checkCondition(property: Map<String, Any>, condition: String): Boolean {
        val parsedCondition = parseCondition(condition)
        println("parsedCondition: $parsedCondition")
        return checkParsedConditions(property, parsedCondition)

    }
}