package bot.wuliang.service

import bot.wuliang.entity.DirectivesEntity
import bot.wuliang.entity.vo.DirectivesVo
import com.baomidou.mybatisplus.extension.service.IService

interface DirectivesService : IService<DirectivesEntity?> {
    /**
     * 查询指令列表
     *
     * @return
     */
    fun selectDirectivesList(directivesEntity: DirectivesEntity?): MutableList<DirectivesVo>

    /**
     * 精确正则匹配查询指令
     */
    fun selectDirectivesMatch(match: String): MutableList<DirectivesEntity>

    /**
     * 添加指令
     *
     * @param directivesEntity 指令实体
     * @return
     */
    fun addDirectives(directivesEntity: DirectivesEntity): Int

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
     * 批量更新指令
     *
     * @param directivesToUpdate
     */
    fun batchUpdateDirectives(directivesToUpdate: MutableList<DirectivesEntity>)
}