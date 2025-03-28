package bot.wuliang.template.entity

import com.baomidou.mybatisplus.annotation.IdType
import com.baomidou.mybatisplus.annotation.TableField
import com.baomidou.mybatisplus.annotation.TableId
import com.baomidou.mybatisplus.annotation.TableName

@TableName("bot_md_template")
data class TemplateEntity(
    @TableId(value = "id", type = IdType.AUTO)
    private val id: Int? = null,

    /**
     * 机器人ID
     */
    @TableField(value = "bot_id")
    private val botId: Long? = null,

    /**
     * 模板名称
     */
    @TableField(value = "template_name")
    private val templateName: String? = null,

    /**
     * 模板内容
     */
    @TableField(value = "content") var content: String? = null,
)
