package bot.wuliang.mapper

import bot.wuliang.entity.DirectivesEntity
import com.baomidou.mybatisplus.core.mapper.BaseMapper
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param

@Mapper
interface DirectivesMapper : BaseMapper<DirectivesEntity?> {
    /**
     * 批量插入指令
     *
     * @param directivesList
     * @return
     */
    fun batchAddDirectives(@Param("directives") directivesList: List<DirectivesEntity>): Int

    /**
     * 根据指令分类ID查询指令
     *
     * @param categoryId
     * @return
     */
    fun findByCategoryId(@Param("categoryId") categoryId: Long): List<DirectivesEntity>

    /**
     * 批量更新指令
     *
     * @param directivesToUpdate
     */
    fun batchUpdateDirectives(directivesToUpdate: MutableList<DirectivesEntity>)
}