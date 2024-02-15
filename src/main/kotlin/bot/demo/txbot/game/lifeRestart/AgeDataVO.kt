package bot.demo.txbot.game.lifeRestart

data class AgeDataVO(
    /**
     * 年龄
     */
    var age: Int? = null,

    /**
     * 对应的事件池
     */
    var eventList: MutableList<String?>? = null
)
