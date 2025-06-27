package bot.wuliang.service

import bot.wuliang.entity.DirectivesEntity
import com.baomidou.mybatisplus.extension.service.IService

interface DirectivesService : IService<DirectivesEntity?> {
    /**
     * 查询指令列表
     *
     * @return
     */
    fun selectDirectivesList(directivesEntity: DirectivesEntity?): List<DirectivesEntity>

    /**
     * 精确正则匹配查询指令
     */
    fun selectDirectivesMatch(match: String): MutableList<DirectivesEntity>

    /**
     * 批量插入指令
     *
     * @param directivesList
     */
    fun batchAddDirectives(directivesList: List<DirectivesEntity>): Int

    /**
     * 根据指令ID查询指令
     *
     * @param categoryIds 指令ID集合
     * @return
     */
    fun findByCategoryIds(categoryIds: List<Long>): List<DirectivesEntity>

    /**
     * 删除指令
     */
    fun deleteDirective(id: Long): Int
}