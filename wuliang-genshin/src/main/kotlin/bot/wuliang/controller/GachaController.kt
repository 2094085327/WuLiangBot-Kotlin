package bot.wuliang.controller

import bot.wuliang.botLog.logAop.SystemLog
import bot.wuliang.botUtil.BotUtils
import bot.wuliang.distribute.annotation.AParameter
import bot.wuliang.distribute.annotation.ActionService
import bot.wuliang.distribute.annotation.Executor
import bot.wuliang.event.ResourceUpdateEvent
import bot.wuliang.respEnum.GenshinRespEnum
import bot.wuliang.utils.InitGenShinData
import bot.wuliang.utils.InitGenShinData.Companion.poolData
import bot.wuliang.utils.MysDataUtil
import bot.wuliang.utils.UpdateGachaResources
import org.springframework.stereotype.Component
import java.util.regex.Matcher

@Component
@ActionService
class GachaController {
    @SystemLog(businessName = "获取原神全部可用卡池")
    @AParameter
    @Executor(action = "全部卡池")
    fun allPool(context: BotUtils.Context) {
        UpdateGachaResources().getDataMain(ResourceUpdateEvent(this))
        val poolList = MysDataUtil().findEachPoolName()
        context.sendMsg(poolList.joinToString("\n"))
    }

    /**
     * 解析输入数据，返回卡池名称、卡池 ID 和卡池类型
     *
     * @param poolData 输入的卡池数据
     * @return Triple<String, String, String> 卡池名称、卡池 ID 和卡池类型
     */
    private fun parsePoolData(poolData: String): Triple<String, String, String> {
        val regex = Regex("(.*?)\\s*-\\s*([^-]*?)(武器|常驻)?\\s*$")
        regex.matchEntire(poolData)?.destructured?.let { (name, id, type) ->
            val poolType = when (type.trim()) {
                "武器" -> "weapon"
                "常驻" -> "permanent"
                else -> "role" // 默认类型
            }
            return Triple(name.trim(), id.trim(), poolType)
        }

        // 尝试匹配不包含连字符的数字格式，如“4.5 武器”或“4.5 常驻”
        val simpleRegex = Regex("(\\d+\\.\\d+)\\s*(武器|常驻)?\\s*$")
        simpleRegex.find(poolData)?.destructured?.let { (id, type) ->
            val poolType = when (type.trim()) {
                "武器" -> "weapon"
                "常驻" -> "permanent"
                else -> "role" // 默认类型
            }
            return Triple("default", id, poolType)
        }
        return Triple("", "", "")
    }

    @SystemLog(businessName = "启用模拟抽卡卡池")
    @AParameter
    @Executor(action = "\\b^启用卡池\\s*(\\S+)")
    fun setOpenPool(context: BotUtils.Context, matcher: Matcher) {
        // 解析输入
        val (poolName, poolId, poolType) = parsePoolData(matcher.group(1))
        if (poolName.isEmpty() || poolId.isEmpty()) {
            context.sendMsg(GenshinRespEnum.POOL_FORMAT_ERROR.message)
            return
        }

        val poolFormat = "$poolName-$poolId"


        val poolFind = MysDataUtil().findPoolData(poolName, poolId)
        if (poolFind == null) {
            context.sendMsg(GenshinRespEnum.POOL_NOTFOUND.message)
            return
        }
        MysDataUtil().changePoolOpen(poolFind, poolFormat, poolType)

        context.sendMsg("已启用卡池「${poolFind.first}」")
        InitGenShinData.initGachaLogData()
    }

    @SystemLog(businessName = "模拟抽卡十连")
    @AParameter
    @Executor(action = "十连")
    fun gacha(context: BotUtils.Context) {
        val detailPoolInfo = poolData

        val itemListData = MysDataUtil().runGacha()
        val itemList = mutableListOf<String>()
        itemListData.forEach { eachItem ->
            itemList.add(eachItem!!.name.toString())
        }

        context.sendMsg("现在启用的卡池是「${detailPoolInfo["poolName"].textValue()}」,如果和你设置的卡池不一样可能是有其他人正在使用哦，可以等一下再尝试~\n" + "你抽中了:$itemList")
    }
}