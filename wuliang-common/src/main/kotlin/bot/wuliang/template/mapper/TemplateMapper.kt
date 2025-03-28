package bot.wuliang.template.mapper

import bot.wuliang.template.entity.TemplateEntity
import com.baomidou.mybatisplus.core.mapper.BaseMapper
import org.apache.ibatis.annotations.Mapper

@Mapper
interface TemplateMapper : BaseMapper<TemplateEntity?> {
}