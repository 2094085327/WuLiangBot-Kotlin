package bot.wuliang.template

import com.baomidou.mybatisplus.extension.service.IService

interface TemplateService : IService<TemplateEntity?> {
    fun insertTemplate(botId: Long, templateName: String, templateContent: String)

    fun searchByBotIdAndTemplateName(botId: Long, templateName: String): TemplateEntity?
}