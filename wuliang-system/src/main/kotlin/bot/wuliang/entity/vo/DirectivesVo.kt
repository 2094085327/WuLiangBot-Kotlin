package bot.wuliang.entity.vo

import com.alibaba.fastjson2.annotation.JSONField
import com.fasterxml.jackson.annotation.JsonFormat
import lombok.AllArgsConstructor
import lombok.Data
import lombok.NoArgsConstructor
import java.util.*

@Data
@AllArgsConstructor
@NoArgsConstructor
data class DirectivesVo(
    /***
     * 指令ID
     */
    var id: Long,

    /**
     * 分类ID
     */
    val categoryId: Long,

    /**
     * 分类名称
     */
    val categoryName: String,

    /**
     * 指令名称
     */
    val directiveName: String,

    /**
     * 指令显示描述
     */
    val description: String,

    /**
     * 指令详细描述
     */
    val detail: String,


    /**
     * 指令正则
     */
    val regex: String,

    /**
     * 是否启用
     */
    val enable: Int,

    /**
     * 删除状态
     */
    val delStatus: Int,


    /** 创建时间  */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @JSONField(format = "yyyy-MM-dd HH:mm:ss")
    private var createTime: Date,

    /** 更新时间  */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @JSONField(format = "yyyy-MM-dd HH:mm:ss")
    private var updateTime: Date
)
