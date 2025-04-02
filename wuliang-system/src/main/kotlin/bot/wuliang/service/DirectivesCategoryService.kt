package bot.wuliang.service

import bot.wuliang.entity.DirectivesCategoryEntity
import com.baomidou.mybatisplus.extension.service.IService

interface DirectivesCategoryService : IService<DirectivesCategoryEntity?> {
    fun selectDirectivesCategoryList(): MutableList<DirectivesCategoryEntity?>

    fun addDirectivesCategory(directivesCategoryEntity: DirectivesCategoryEntity): Int

    fun findByCategoryNamesIn(categoryNameList: List<String>): List<DirectivesCategoryEntity>

    fun batchAddCategories(categories: List<DirectivesCategoryEntity>): Int
}