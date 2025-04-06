package bot.wuliang.mapper

import bot.wuliang.entity.DirectivesCategoryEntity
import com.baomidou.mybatisplus.core.mapper.BaseMapper
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param

@Mapper
interface DirectivesCategoryMapper : BaseMapper<DirectivesCategoryEntity?> {
    fun findByCategoryNamesIn(@Param("categoryNames") categoryNameList: List<String>): List<DirectivesCategoryEntity>


    fun batchAddCategories(@Param("categories") categories: List<DirectivesCategoryEntity>): Int

    fun batchUpdateCategories(@Param("categories") categories: List<DirectivesCategoryEntity>): Int
}