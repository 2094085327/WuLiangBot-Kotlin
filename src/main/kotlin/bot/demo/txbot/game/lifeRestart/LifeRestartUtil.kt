package bot.demo.txbot.game.lifeRestart


/**
 * @description: 人生重开用到的方法
 * @author Nature Zero
 * @date 2024/2/16 13:24
 */
class LifeRestartUtil {
    data class UserInfo(
        val userId: String,
        var attributes: Map<String, Int>? = null,
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

    /**
     * 分配属性
     *
     */
    fun assignAttributes() {

    }

    /**
     * 使用游戏购买额外属性
     *
     */
    fun getAddAttributes() {

    }
}