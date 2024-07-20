package bot.demo.txbot.common.database.template

import com.baomidou.mybatisplus.core.mapper.BaseMapper
import org.apache.ibatis.annotations.Mapper

@Mapper
interface TemplateMapper : BaseMapper<TemplateEntity?> {
}