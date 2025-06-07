package bot.wuliang.core.page

import bot.wuliang.exception.RespBeanEnum
import com.baomidou.mybatisplus.core.metadata.IPage
import java.io.Serializable


/**
 * @description: 表格分页数据对象
 * @author Nature Zero
 * @date 2025/6/6 10:57
 */
data class TableDataInfo(

    /** 总记录数  */

    var total: Long = 0,

    /** 列表数据  */

    var rows: List<*>? = null,

    /** 消息状态码  */

    var code: Long = RespBeanEnum.ERROR.code,

    /** 消息内容  */

    var msg: String? = null

) : Serializable {
    fun <T> build(page: IPage<T>): TableDataInfo {
        val rspData = TableDataInfo()
        rspData.code = RespBeanEnum.SUCCESS.code
        rspData.msg = "查询成功"
        rspData.rows = page.records
        rspData.total = page.total
        return rspData
    }

    fun <T> build(list: List<T>): TableDataInfo {
        val rspData = TableDataInfo()
        rspData.code = RespBeanEnum.SUCCESS.code
        rspData.msg = "查询成功"
        rspData.rows = list
        rspData.total = list.size.toLong()
        return rspData
    }

    fun build(): TableDataInfo {
        val rspData = TableDataInfo()
        rspData.code = RespBeanEnum.SUCCESS.code
        rspData.msg = "查询成功"
        return rspData
    }
}