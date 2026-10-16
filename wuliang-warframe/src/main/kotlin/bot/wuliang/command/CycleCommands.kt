package bot.wuliang.command

import bot.wuliang.adapter.context.ExecutionContext
import bot.wuliang.distribute.annotation.AParameter
import bot.wuliang.distribute.annotation.ActionService
import bot.wuliang.distribute.annotation.Executor
import bot.wuliang.logAop.SystemLog
import bot.wuliang.worldstate.service.WorldCycleService
import bot.wuliang.worldstate.source.WorldStateUnavailableException
import org.springframework.stereotype.Component

/** 平原周期指令，共用周期算法并统一处理未发布或获取失败的提示。 */
@Component
@ActionService
class CycleCommands(
    private val worldCycleService: WorldCycleService,
) {

    /**
     * 生成魔胎之境周期消息；[context] 不为空时同时发送消息。
     *
     * @return 当前周期信息或数据不可用提示。
     */
    @SystemLog(businessName = "获取火卫二循环信息")
    @AParameter
    @Executor(action = "\\b(火卫二状态|火星状态|火星平原状态|火卫二平原状态|火卫二平原|火星平原)\\b")
    suspend fun phobosStatus(context: ExecutionContext?): String = sendCycleStatus(context, "cambion")

    /**
     * 生成希图斯昼夜周期消息；[context] 不为空时同时发送消息。
     *
     * @return 当前周期信息或数据不可用提示。
     */
    @SystemLog(businessName = "获取夜灵平原昼夜循环信息")
    @AParameter
    @Executor(action = "\\b(地球平原状态|希图斯状态|夜灵平原状态|地球平原|夜灵平原)\\b")
    suspend fun cetusCycle(context: ExecutionContext?): String = sendCycleStatus(context, "cetus")

    /**
     * 生成地球昼夜周期消息；[context] 不为空时同时发送消息。
     *
     * @return 当前周期信息或数据不可用提示。
     */
    @SystemLog(businessName = "获取地球昼夜循环信息")
    @AParameter
    @Executor(action = "\\b(地球状态|地球时间|地球)\\b")
    suspend fun earthCycle(context: ExecutionContext?): String = sendCycleStatus(context, "earth")

    /**
     * 生成奥布山谷冷热周期消息；[context] 不为空时同时发送消息。
     *
     * @return 当前周期信息或数据不可用提示。
     */
    @SystemLog(businessName = "获取金星冷热循环信息")
    @AParameter
    @Executor(action = "\\b(金星状态|金星平原状态|福尔图娜状态|福尔图娜平原状态|金星平原|福尔图娜)\\b")
    suspend fun venusStatus(context: ExecutionContext?): String = sendCycleStatus(context, "vallis")

    /** 基于同一次周期查询生成各平原文本，获取失败或缺少有效记录时使用对应提示。 */
    private fun cycleMessages(): Map<String, String> {
        val titles = linkedMapOf(
            "cambion" to "火卫二平原", "cetus" to "希图斯平原",
            "earth" to "地球昼夜", "vallis" to "金星平原"
        )
        val statuses = try {
            worldCycleService.getCycleStatuses()
        } catch (error: WorldStateUnavailableException) {
            return titles.mapValues { error.message.orEmpty() }
        }
        return titles.mapValues { (id, title) ->
            val status = statuses[id]
            if (status == null) "暂时无法获取${title}状态，官方尚未发布有效的周期数据，请稍后重试"
            else "当前${title}的状态为 ${status.displayState}\n" +
                    "开始时间:${status.activation}\n" +
                    "结束时间:${status.expiry}\n" +
                    "剩余:${status.timeLeft}"
        }
    }

    /** 按周期标识 [id] 返回消息，并在 [context] 不为空时发送到当前会话。 */
    private suspend fun sendCycleStatus(context: ExecutionContext?, id: String): String {
        val message = cycleMessages().getValue(id)
        context?.sender?.sendText(message)
        return message
    }

    /** 获取同一次查询中的全部平原周期，去重后合并发送。 */
    @SystemLog(businessName = "获取全部平原循环信息")
    @AParameter
    @Executor(action = "\\b(平原|全部平原|平原时间)\\b")
    suspend fun allPlain(context: ExecutionContext) {
        context.sender.sendText(cycleMessages().values.distinct().joinToString("\n\n"))
    }
}