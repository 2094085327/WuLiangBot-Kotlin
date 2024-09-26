package bot.demo.txbot.game.lifeRestart.vo

import bot.demo.txbot.game.lifeRestart.TalentDataVo


/**
 * @description: 读取Excel时，封装读取的每一行的数据
 * @author Nature Zero
 * @date 2024/2/14 20:18
 */
data class EventDataVO(
    /**
     * id
     */
    var id: String? = null,

    /**
     * 事件
     */
    var event: String? = null,

    /**
     * 稀有度
     */
    var grade: Int? = null,

    /**
     * 追加事件
     */
    var postEvent: String? = null,

    /**
     * 颜值变化
     */
    var effectChr: Int? = null,

    /**
     * 智力变化
     */
    var effectInt: Int? = null,

    /**
     * 体质变化
     */
    var effectStr: Int? = null,

    /**
     * 家境变化
     */
    var effectMny: Int? = null,

    /**
     * 快乐变化
     */
    var effectSpr: Int? = null,

    /**
     * 生命变化
     */
    var effectLif: Int? = null,

    /**
     * 年龄变化
     */
    var effectAge: Int? = null,

    /**
     * 非随机事件
     */
    var noRandom: Int? = null,

    /**
     * 有某事件时才能被随机到
     */
    var include: String? = null,

    /**
     * 有某事件时一定随机不到
     */
    var exclude: String? = null,

    /**
     * 优先分支数组，第一个为优先分支，第二个为次优先，第三个为普通分支
     */
    var branch: MutableList<String?>? = null,

    /**
     * 每个事件的改变的属性变化
     */
    var eachChange: MutableMap<String, Int> = mutableMapOf(),

    /**
     * 当前事件触发的天赋
     */
    var activeTalent: List<TalentDataVo> = mutableListOf()

)