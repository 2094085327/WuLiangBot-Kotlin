package bot.demo.txbot.game.lifeRestart.vo

data class AgeDataVO(
    /**
     * 年龄
     */
    var age: Int? = null,

    /**
     * 对应的事件池
     */
    var eventList: MutableList<Any?>? = null
)
