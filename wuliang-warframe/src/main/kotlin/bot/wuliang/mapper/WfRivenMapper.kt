package bot.wuliang.mapper

import bot.wuliang.entity.WfRivenEntity
import com.baomidou.mybatisplus.core.mapper.BaseMapper
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param

@Mapper
interface WfRivenMapper : BaseMapper<WfRivenEntity?> {
    /**
     * 生成一条多 values 的 upsert，避免逐条调用 BaseMapper.insert 带来的数据库往返。
     * 调用方必须保证 [list] 非空，否则 foreach 无法生成合法的 VALUES 子句。
     */
    fun insertOrUpdateBatch(@Param("list") list: List<WfRivenEntity>)

    /** 查询标准紫卡武器目录；玄骸和信条武器由各自目录维护。 */
    fun selectAllRiven(): List<WfRivenEntity>
}