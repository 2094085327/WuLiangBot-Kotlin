package bot.wuliang.mapper

import bot.wuliang.entity.WfOtherNameEntity
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param

/** 别名表查询*/
@Mapper
interface WfAliasMapper {
    fun findEnglishName(@Param("otherName") otherName: String): String?
    fun save(@Param("enItemName") enItemName: String, @Param("otherName") otherName: String): Int
    fun findAll(): List<WfOtherNameEntity>
    fun delete(@Param("id") id: Int)
    fun update(@Param("id") id: Int, @Param("otherName") otherName: String)
}