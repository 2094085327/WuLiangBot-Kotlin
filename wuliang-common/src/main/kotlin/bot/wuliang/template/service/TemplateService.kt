package bot.wuliang.template.service

import bot.wuliang.template.entity.TemplateEntity
import com.baomidou.mybatisplus.extension.service.IService

interface TemplateService : IService<TemplateEntity?> {
    fun insertTemplate(botId: String, templateName: String, templateContent: String)

    fun searchByBotIdAndTemplateName(botId: String, templateName: String): TemplateEntity?
}