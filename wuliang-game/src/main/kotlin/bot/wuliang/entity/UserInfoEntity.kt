package bot.wuliang.entity

import bot.wuliang.config.*
import java.io.Serializable

data class UserInfoEntity(
    val userId: String? = null,
    var attributes: Map<*, *>? = null,
    var age: Int = 0,
    var events: MutableList<String> = mutableListOf(),
    var property: MutableMap<String, Int> = mutableMapOf(),
    var propertyDistribution: Boolean = false,
    var talent: MutableList<bot.wuliang.entity.vo.TalentDataVo> = mutableListOf(),
    var isEnd: Boolean? = false,
    var gameTimes: Int = 0,
    var achievement: Int = 0,
    var randomTalentTemp: MutableList<bot.wuliang.entity.vo.TalentDataVo> = mutableListOf(),
    var status: Int = 20,
    var talentSelectLimit: Int = 3,
    var maxProperty: MutableMap<String, Int> = mutableMapOf(
        CHR to 0,
        MNY to 0,
        SPR to 0,
        INT to 0,
        STR to 0,
        SUM to 0,
    )
) : Serializable
