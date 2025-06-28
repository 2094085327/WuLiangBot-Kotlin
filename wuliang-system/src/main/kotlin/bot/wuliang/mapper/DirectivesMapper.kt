package bot.wuliang.mapper

import bot.wuliang.entity.DirectivesEntity
import com.baomidou.mybatisplus.core.mapper.BaseMapper
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param

@Mapper
interface DirectivesMapper : BaseMapper<DirectivesEntity?> {
    /**
     * 查询指令列表
     */
    fun selectDirectivesList(directivesEntity: DirectivesEntity?): List<DirectivesEntity>

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
     * @param categoryIds 指令分类ID集合
     * @return
     */
    fun findByCategoryIds(@Param("categoryIds") categoryIds: List<Long>): List<DirectivesEntity>

    /**
     * 返回指令匹配列表
     */
    fun selectDirectivesMatch(@Param("match") match: String): MutableList<DirectivesEntity>

    /**
     * 删除指令
     */
    fun deleteDirectiveById(id: Long): Int
}