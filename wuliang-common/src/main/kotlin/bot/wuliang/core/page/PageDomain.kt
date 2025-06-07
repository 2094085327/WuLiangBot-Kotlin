package bot.wuliang.core.page

import lombok.Data

@Data
data class PageDomain(
    /** 当前记录起始索引  */
    var pageNum: Int? = null,

    /** 每页显示记录数  */

    var pageSize: Int? = null,

    /** 排序列  */

    var orderByColumn: String? = null,

    /** 排序的方向desc或者asc  */

    var isAsc: String = "asc",

    /** 分页参数合理化  */

    var reasonable: Boolean = true,
)