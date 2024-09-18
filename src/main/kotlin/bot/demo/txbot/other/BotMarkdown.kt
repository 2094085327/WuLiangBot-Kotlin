package bot.demo.txbot.other

import bot.demo.txbot.common.botUtil.BotUtils.Context
import bot.demo.txbot.common.database.template.TemplateService
import com.mikuac.shiro.annotation.AnyMessageHandler
import com.mikuac.shiro.annotation.MessageHandlerFilter
import com.mikuac.shiro.annotation.common.Shiro
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Component
import java.util.regex.Matcher


/**
 * @description: Markdown相关
 * @author Nature Zero
 * @date 2024/7/20 上午10:09
 */
@Shiro
@Component
class BotMarkdown(@Autowired private val templateService: TemplateService) {
    @AnyMessageHandler
    @MessageHandlerFilter(cmd = "增加模板 (.*)")
    fun addTemplate(context: Context, matcher: Matcher) {
        val templateInfo = matcher.group(1)
        val parts = templateInfo.split(" ")
        val templateName = parts.first()
        println(templateName)
        val templateContent = parts.drop(1).joinToString(" ")
        println(templateContent)
        templateService.insertTemplate(context.getBot().selfId, templateName, templateContent)
    }
}