package bot.wuliang.service

import bot.wuliang.entity.DirectivesCategoryEntity
import com.baomidou.mybatisplus.extension.service.IService

interface DirectivesCategoryService : IService<DirectivesCategoryEntity?> {
    /**
     * 查询指令分类列表
     *
     * @return
     */
    fun selectDirectivesCategoryList(): MutableList<DirectivesCategoryEntity?>

    /**
     * 添加指令分类
     *
     * @param directivesCategoryEntity 指令分类实体
     * @return
     */
    fun addDirectivesCategory(directivesCategoryEntity: DirectivesCategoryEntity): Int

    /**
     * 根据分类名称列表查询指令分类
     *
     * @param categoryNameList 分类名称列表
     * @return
     */
    fun findByCategoryNamesIn(categoryNameList: List<String>): List<DirectivesCategoryEntity>

    /**
     * 批量添加指令分类
     *
     * @param categories 分类列表
     * @return
     */
    fun batchAddCategories(categories: List<DirectivesCategoryEntity>): List<DirectivesCategoryEntity>

    /**
     * 批量更新指令分类
     * @param categories 分类列表
     */
    fun batchUpdateCategories(categories: List<DirectivesCategoryEntity>)
}