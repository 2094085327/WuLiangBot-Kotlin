package bot.wuliang.command

import bot.wuliang.adapter.context.ExecutionContext
import bot.wuliang.distribute.annotation.AParameter
import bot.wuliang.distribute.annotation.ActionService
import bot.wuliang.distribute.annotation.Executor
import bot.wuliang.logAop.SystemLog
import bot.wuliang.translation.PublicExportService
import bot.wuliang.worldstate.model.BountyGroup
import bot.wuliang.worldstate.presentation.BountyImageService
import bot.wuliang.worldstate.source.WorldStateUnavailableException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Component

/** 六类赏金及汇总指令；别名共用查询和图片缓存。 */
@Component
@ActionService
class BountyCommands(
    private val images: BountyImageService,
    private val translations: PublicExportService,
) {
    @SystemLog(businessName = "获取地球赏金")
    @AParameter
    @Executor(action = "^(希图斯赏金|地球赏金|地球平原赏金)$")
    suspend fun earth(context: ExecutionContext) = send(context, BountyGroup.EARTH)

    @SystemLog(businessName = "获取金星赏金")
    @AParameter
    @Executor(action = "^(索拉里斯赏金|金星赏金|金星平原赏金)$")
    suspend fun venus(context: ExecutionContext) = send(context, BountyGroup.VENUS)

    @SystemLog(businessName = "获取火卫二赏金")
    @AParameter
    @Executor(action = "^(英择谛赏金|火卫二赏金|火卫二平原赏金|火星赏金)$")
    suspend fun deimos(context: ExecutionContext) = send(context, BountyGroup.DEIMOS)

    @SystemLog(businessName = "获取扎里曼赏金")
    @AParameter
    @Executor(action = "^(坚守者赏金|扎里曼赏金)$")
    suspend fun zariman(context: ExecutionContext) = send(context, BountyGroup.ZARIMAN)

    @SystemLog(businessName = "获取圣所赏金")
    @AParameter
    @Executor(action = "^(科纬兽赏金|科维兽赏金|圣所赏金|解刨圣所赏金|解剖圣所赏金)$")
    suspend fun sanctum(context: ExecutionContext) = send(context, BountyGroup.SANCTUM)

    @SystemLog(businessName = "获取1999赏金")
    @AParameter
    @Executor(action = "^(六人组赏金|1999赏金)$")
    suspend fun hex(context: ExecutionContext) = send(context, BountyGroup.HEX)

    @SystemLog(businessName = "获取全部赏金")
    @AParameter
    @Executor(action = "^赏金$")
    suspend fun all(context: ExecutionContext) = send(context, null)

    private suspend fun send(context: ExecutionContext, group: BountyGroup?) {
        if (!translations.ready) {
            context.sender.sendText(PublicExportService.UNAVAILABLE_MESSAGE)
            return
        }
        val url = try {
            withContext(Dispatchers.IO) { images.imageUrl(group) }
        } catch (error: WorldStateUnavailableException) {
            context.sender.sendText(error.message.orEmpty())
            return
        }
        context.sender.sendImage(url)
    }
}