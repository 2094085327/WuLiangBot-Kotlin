package bot.wuliang.handle

import bot.wuliang.adapter.context.ExecutionContext
import bot.wuliang.config.DirectivesConfig.DIRECTIVES_KEY
import bot.wuliang.distribute.annotation.AParameter
import bot.wuliang.distribute.annotation.ActionService
import bot.wuliang.distribute.annotation.Executor
import bot.wuliang.entity.DirectivesEntity
import bot.wuliang.imageProcess.WebImgUtil
import bot.wuliang.logAop.SystemLog
import bot.wuliang.otherUtil.OtherUtil.STConversion.toMd5
import bot.wuliang.redis.RedisService
import bot.wuliang.service.DirectivesService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.util.regex.Matcher

@Component
@ActionService
class HelpHandle @Autowired constructor(
    private val webImgUtil: WebImgUtil,
    private val redisService: RedisService,
    private val directivesService: DirectivesService
) {
    @SystemLog(businessName = "获取帮助菜单")
    @AParameter
    @Executor(action = "\\b(帮助|菜单|help)\\b")
    suspend fun help(context: ExecutionContext) {
        val directivesList = getDirectivesList()
        val imgName = "help-" + (directivesList?.toMd5() ?: System.currentTimeMillis().toString())

        val imageData = WebImgUtil.ImgData(
            imgName = imgName,
            element = "#app",
            url = "http://${webImgUtil.frontendAddress}/system/help"
        )
        val url = webImgUtil.getImgUrl(imageData)
        context.sender.sendImage(url)
    }

    @SystemLog(businessName = "获取指定指令帮助详情")
    @AParameter
    @Executor(action = "^help\\s+(.+)$")
    suspend fun helpDetail(context: ExecutionContext, matcher: Matcher) {
        val directiveInput = matcher.group(1).trim()

        val directive = getDirectivesList()
            ?.filter { it.enable == 1 }
            ?.firstOrNull { item ->
                val regex = item.regex
                !regex.isNullOrBlank() && runCatching { regex.toRegex().matches(directiveInput) }.getOrDefault(false)
            }

        if (directive == null || directive.id == null) {
            context.sender.sendText("没有找到「$directiveInput」对应的指令，发送 help 查看全部指令")
            return
        }

        val imageData = WebImgUtil.ImgData(
            imgName = "help-detail-${directive.id}-${directive.toMd5()}",
            element = "#app",
            url = "http://${webImgUtil.frontendAddress}/system/help/detail?id=${directive.id}",
            waitElement = ".detailCard"
        )
        val url = webImgUtil.getImgUrl(imageData)
        context.sender.sendImage(url)
    }

    private fun getDirectivesList(): List<DirectivesEntity>? {
        return if (redisService.hasKey(DIRECTIVES_KEY)) {
            redisService.getValueTyped<List<DirectivesEntity>>(DIRECTIVES_KEY)
        } else {
            directivesService.selectDirectivesList(null).also { directivesList ->
                if (directivesList.isNotEmpty()) {
                    redisService.setValue(DIRECTIVES_KEY, directivesList)
                }
            }
        }
    }
}