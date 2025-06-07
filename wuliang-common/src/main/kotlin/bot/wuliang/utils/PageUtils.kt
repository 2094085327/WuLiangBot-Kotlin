package bot.wuliang.utils

import bot.wuliang.core.page.PageDomain
import bot.wuliang.core.page.TableSupport
import com.baomidou.mybatisplus.core.metadata.IPage
import com.baomidou.mybatisplus.extension.plugins.pagination.Page


/**
 * @description: 分页工具类
 * @author Nature Zero
 * @date 2025/6/6 10:48
 */
class PageUtils {
    /**
     * 设置请求分页数据
     */
    fun <T> startPage(): IPage<T> {
        val pageDomain: PageDomain = TableSupport.buildPageRequest()
        val pageNum: Int? = pageDomain.pageNum
        val pageSize: Int? = pageDomain.pageSize

        return if (StringUtils.isNotNull(pageNum) && StringUtils.isNotNull(pageSize)) {
            val page = Page<T>(pageNum!!.toLong(), pageSize!!.toLong())
            page
        } else {
            Page(1, 20) // 默认分页
        }
    }
}