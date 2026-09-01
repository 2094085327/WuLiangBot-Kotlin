package bot.wuliang.mapper

import bot.wuliang.entity.WfRivenAttributeEntity
import com.baomidou.mybatisplus.core.mapper.BaseMapper
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param

@Mapper
interface WfRivenAttributeMapper : BaseMapper<WfRivenAttributeEntity?> {
    /**
     * 将 v2 属性目录作为一条多 values SQL 写入；主键冲突时同步更新可变的目录字段。
     * 调用方必须保证 [list] 非空，否则 foreach 无法生成合法的 VALUES 子句。
     */
    fun insertOrUpdateBatch(@Param("list") list: List<WfRivenAttributeEntity>)
}
