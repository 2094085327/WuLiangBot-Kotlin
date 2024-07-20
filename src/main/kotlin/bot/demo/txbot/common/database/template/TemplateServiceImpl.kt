package bot.demo.txbot.common.database.template

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service


/**
 * @description: Md模板实现类
 * @author Nature Zero
 * @date 2024/7/20 上午10:02
 */
@Service
class TemplateServiceImpl(@Autowired private val templateMapper: TemplateMapper) :
    ServiceImpl<TemplateMapper?, TemplateEntity?>(), TemplateService {
    override fun insertTemplate(botId: Long, templateName: String, templateContent: String) {
        val templateInfo = TemplateEntity(botId = botId, templateName = templateName, content = templateContent)
        val queryWrapper = QueryWrapper<TemplateEntity>().eq("bot_id", botId).eq("template_name", templateName)

        val existGachaInfo = templateMapper.selectOne(queryWrapper)
        if (existGachaInfo == null) templateMapper.insert(templateInfo)
        else templateMapper.update(templateInfo, queryWrapper)
    }

    override fun searchByBotIdAndTemplateName(botId: Long, templateName: String): TemplateEntity? {
        val queryWrapper = QueryWrapper<TemplateEntity>().eq("bot_id", botId).eq("template_name", templateName)
        return templateMapper.selectOne(queryWrapper)
    }
}