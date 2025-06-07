package bot.wuliang.core.page

import bot.wuliang.utils.ServletUtils


/**
 * @description: 表格数据处理
 * @author Nature Zero
 * @date 2025/6/6 11:22
 */
object TableSupport {
    /**
     * 当前记录起始索引
     */
    private const val PAGE_NUM: String = "pageNum"

    /**
     * 每页显示记录数
     */
    private const val PAGE_SIZE: String = "pageSize"

    /**
     * 封装分页对象
     */
    private fun getPageDomain(): PageDomain {
        val pageDomain = PageDomain()
        pageDomain.pageNum = ServletUtils.getParameterToInt(PAGE_NUM)
        pageDomain.pageSize = ServletUtils.getParameterToInt(PAGE_SIZE)
        return pageDomain
    }

    fun buildPageRequest(): PageDomain {
        return getPageDomain()
    }
}