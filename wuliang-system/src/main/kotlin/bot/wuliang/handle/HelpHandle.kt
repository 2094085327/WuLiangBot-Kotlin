package bot.wuliang.handle

import bot.wuliang.botLog.logAop.SystemLog
import bot.wuliang.utils.BotUtils
import bot.wuliang.config.DirectivesConfig.DIRECTIVES_KEY
import bot.wuliang.distribute.annotation.AParameter
import bot.wuliang.distribute.annotation.ActionService
import bot.wuliang.distribute.annotation.Executor
import bot.wuliang.entity.DirectivesEntity
import bot.wuliang.imageProcess.WebImgUtil
import bot.wuliang.otherUtil.OtherUtil.STConversion.toMd5
import bot.wuliang.redis.RedisService
import bot.wuliang.service.DirectivesService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component

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
    fun help(context: BotUtils.Context) {
        var imgName = "help-"
        val directivesList: List<DirectivesEntity>?

        if (redisService.hasKey(DIRECTIVES_KEY)) {
            directivesList = redisService.getValueTyped<List<DirectivesEntity>>(DIRECTIVES_KEY)
        } else {
            directivesList = directivesService.selectDirectivesList(null)
            if (directivesList.isNotEmpty()) {
                redisService.setValue(DIRECTIVES_KEY, directivesList)
            }
        }

        imgName += directivesList?.toMd5() ?: System.currentTimeMillis().toString()


        val imageData = WebImgUtil.ImgData(
            imgName = imgName,
            element = "#app",
            url = "http://${webImgUtil.frontendAddress}/system/help"
        )
        webImgUtil.sendNewImage(context, imageData)
    }
}