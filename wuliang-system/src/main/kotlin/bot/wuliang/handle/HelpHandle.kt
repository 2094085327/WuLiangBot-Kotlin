package bot.wuliang.handle

import bot.wuliang.adapter.context.ExecutionContext
import bot.wuliang.distribute.annotation.AParameter
import bot.wuliang.distribute.annotation.ActionService
import bot.wuliang.distribute.annotation.Executor
import bot.wuliang.imageProcess.WebImgUtil
import bot.wuliang.logAop.SystemLog
import bot.wuliang.utils.DirectivesUtils
import bot.wuliang.utils.helpDetailImageName
import bot.wuliang.utils.helpMenuImageName
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.util.regex.Matcher

@Component
@ActionService
class HelpHandle @Autowired constructor(
    private val webImgUtil: WebImgUtil,
    private val directives: DirectivesUtils,
) {
    @SystemLog(businessName = "获取帮助菜单")
    @AParameter
    @Executor(action = "\\b(帮助|菜单|help)\\b")
    suspend fun help(context: ExecutionContext) {
        val imageData = WebImgUtil.ImgData(
            imgName = directives.getDirectivesList().helpMenuImageName(),
            element = "#app",
            url = "http://${webImgUtil.frontendAddress}/system/help",
            waitElement = ".commands .command"
        )
        val url = webImgUtil.getImgUrl(imageData)
        context.sender.sendImage(url)
    }

    @SystemLog(businessName = "获取指定指令帮助详情")
    @AParameter
    @Executor(action = "^help\\s+(.+)$")
    suspend fun helpDetail(context: ExecutionContext, matcher: Matcher) {
        val directiveInput = matcher.group(1).trim()

        val directive = directives.getDirectivesList().firstOrNull { item ->
            val regex = item.regex
            item.enable == 1 && !regex.isNullOrBlank() &&
                runCatching { regex.toRegex().matches(directiveInput) }.getOrDefault(false)
        }

        if (directive == null || directive.id == null) {
            context.sender.sendText("没有找到「$directiveInput」对应的指令，发送 help 查看全部指令")
            return
        }

        val imageData = WebImgUtil.ImgData(
            imgName = directive.helpDetailImageName(),
            element = "#app",
            url = "http://${webImgUtil.frontendAddress}/system/help/detail?id=${directive.id}",
            waitElement = ".detailCard"
        )
        val url = webImgUtil.getImgUrl(imageData)
        context.sender.sendImage(url)
    }
}