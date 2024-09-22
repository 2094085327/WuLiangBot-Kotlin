package bot.demo.txbot.warframe.vo

import bot.demo.txbot.warframe.WfUtil


/**
 * @description: Warframe Status Vo层
 * @author Nature Zero
 * @date 2024/8/21 上午9:38
 */
class WfStatusVo {
    /**
     * 裂缝信息
     *
     * @property tierLich  古纪
     * @property tierMeso 前纪
     * @property tierNeo 中纪
     * @property tierAxi 后纪
     * @property tierRequiem 安魂
     * @property tierOmnia 全能
     */
    data class FissureList(
        var tierLich: MutableList<FissureDetail> = mutableListOf(),
        var tierMeso: MutableList<FissureDetail> = mutableListOf(),
        var tierNeo: MutableList<FissureDetail> = mutableListOf(),
        var tierAxi: MutableList<FissureDetail> = mutableListOf(),
        var tierRequiem: MutableList<FissureDetail> = mutableListOf(),
        var tierOmnia: MutableList<FissureDetail> = mutableListOf(),
        var fissureType: String? = ""
    )

    /**
     * 裂缝详情
     *
     * @property eta 截止时间
     * @property node 地点
     * @property missionType 任务类型
     * @property enemyKey 敌人类型
     */
    data class FissureDetail(
        val eta: String? = null,
        val node: String? = null,
        val missionType: String? = null,
        val enemyKey: String? = null,
    )

    /**
     * 虚空商人货物
     *
     * @property item 物品名
     * @property ducats 杜卡德金币
     * @property credits 现金
     */
    data class VoidTraderItem(
        val item: String? = null,
        val ducats: Int? = null,
        val credits: String? = null
    )

    /**
     * 突击任务信息
     *
     * @property missionType 任务类型
     * @property modifier 敌方强化
     * @property node 任务地点
     */
    data class Variants(
        val missionType: String? = null,
        val modifier: String? = null,
        val node: String? = null,
    )

    /**
     * 执刑官任务信息
     *
     * @property node 任务地点
     * @property type 任务类型
     */
    data class Missions(
        val node: String? = null,
        val type: String? = null,
    )

    /**
     * 执刑官突击信息
     *
     * @property faction 阵营
     * @property boss Boss名称
     * @property rewardItem 奖励物品
     * @property taskList 任务列表
     * @property eta 剩余时间
     */
    data class ArchonHuntEntity(
        val faction: String? = null,
        val boss: String? = null,
        val rewardItem: String? = null,
        val taskList: List<Missions>? = null,
        var eta: String? = null,
        val nextBoss: String? = null,
        val nextRewardItem: String? = null
    )

    /**
     * 每日突击信息
     *
     * @property faction 阵营
     * @property boss Boss名称
     * @property taskList 任务列表
     * @property eta 剩余时间
     */
    data class SortieEntity(
        val faction: String? = null,
        val boss: String? = null,
        val taskList: List<Variants>? = null,
        var eta: String? = null
    )

    /**
     * 钢铁之路信息
     *
     * @property currentName 当前可兑换物品名称
     * @property currentCost 当前可兑换物品价格
     * @property remaining 剩余时间
     * @property nextName 下一个可兑换物品名称
     * @property nextCost 下一个可兑换物品价格
     */
    data class SteelPathEntity(
        val currentName: String? = null,
        val currentCost: Int? = null,
        var remaining: String? = null,
        val nextName: String? = null,
        val nextCost: Int? = null
    )

    /**
     * 虚空商人信息
     *
     * @property location 地点
     * @property time 离开时间
     * @property items 带来的物品
     */
    data class VoidTraderEntity(
        val location: String? = null,
        var time: String? = null,
        val items: List<VoidTraderItem>? = null
    )

    /**
     * 午夜电波任务信息
     *
     * @property title 任务名称
     * @property desc 任务描述
     * @property reputation 声望
     * @property isDaily 是否为日常任务
     */
    data class NightWaveChallenges(
        val title: String,
        val desc: String,
        val reputation: Int,
        val isDaily: Boolean
    )

    /**
     * 午夜电波信息
     *
     * @property activation 开始后过去的时间
     * @property startString 开始时间
     * @property expiry 结束时间
     * @property expiryString 剩余时间的字符串
     * @property activeChallenges 任务列表
     */
    data class NightWaveEntity(
        val activation: String,
        val startString: String,
        val expiry: String,
        val expiryString: String,
        val activeChallenges: List<NightWaveChallenges>
    )

    /**
     * 入侵信息
     *
     * @property itemString 物品名称
     * @property factions 阵营
     */
    data class Invasions(
        val itemString: String,
        val factions: String,
    )

    /**
     * 入侵信息 Entity
     *
     * @property node 入侵节点
     * @property invasionsDetail 入侵详情
     * @property completion 入侵完成度
     */
    data class InvasionsEntity(
        val node: String,
        val invasionsDetail: List<Invasions>,
        val completion: Double
    )

    /**
     * 灵化信息
     *
     * @property thisWeekData 本周数据
     * @property nextWeekData 下周数据
     * @property remainTime 剩余时间
     */
    data class IncarnonEntity(
        val thisWeekData: WfUtil.Data? = null,
        val nextWeekData: WfUtil.Data? = null,
        var remainTime: String? = null
    )

    /**
     * 双衍平原状态信息
     *
     * @property currentState 当前状态
     * @property damageType 伤害类型
     * @property npc 当前出现的NPC列表
     * @property excludeNpc 当前不出现的NPC列表
     * @property excludePlace 当前不出现的地点列表
     * @property noExcludePlace 当前出现的地点列表
     * @property remainTime 距离下个状态的剩余时间
     * @property nextState 下个状态
     * @property nextExcludePlace 下个状态不出现的地点列表
     */
    data class MoodSpiralsEntity(
        val currentState: String? = null,
        val damageType: String? = null,
        val npc: List<Map<String, String>>? = null,
        val excludeNpc: List<Map<String, String>>? = null,
        val excludePlace: List<String>? = null,
        val noExcludePlace: List<String>? = null,
        var remainTime: String? = null,
        val nextState: String? = null,
        val nextExcludePlace: List<String>? = null,
    )

    /**
     * 世界状态信息
     *
     * @property displayState 状态
     * @property activation 开始时间
     * @property expiry 结束时间
     * @property timeLeft 剩余时间
     */
    data class WordStatus(
        val displayState: String? = null,
        val activation: String? = null,
        val expiry: String? = null,
        val timeLeft: String? = null
    )
}