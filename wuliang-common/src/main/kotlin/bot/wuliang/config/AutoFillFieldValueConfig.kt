package bot.wuliang.config

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler
import org.apache.ibatis.reflection.MetaObject
import org.springframework.stereotype.Component
import java.util.*

@Component
class AutoFillFieldValueConfig :MetaObjectHandler {

    /**
     * 创建时间
     */
    private val createTime: String = "createTime"

    /**
     * 更新时间字段
     */
    private val updateTime: String = "updateTime"

    /**
     * 删除字段
     */
    private val delStatus: String = "delStatus"


    override fun insertFill(metaObject: MetaObject?) {
        val now = Date()
        this.setFieldValByName(createTime, now, metaObject)
        this.setFieldValByName(updateTime, now, metaObject)
        this.setFieldValByName(delStatus, 0, metaObject)
    }

    override fun updateFill(metaObject: MetaObject?) {
        val now = Date()
        this.setFieldValByName(updateTime, now, metaObject)
    }
}