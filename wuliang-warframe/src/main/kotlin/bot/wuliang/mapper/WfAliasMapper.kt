package bot.wuliang.mapper

import bot.wuliang.entity.WfOtherNameEntity
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param

/** 别名表查询*/
@Mapper
interface WfAliasMapper {
    /** 根据查询别名 [otherName] 查找英文物品名称；没有对应记录时返回 null。 */
    fun findEnglishName(@Param("otherName") otherName: String): String?
    /**
     * 保存英文物品名称 [enItemName] 与查询别名 [otherName] 的对应关系。
     *
     * @return 数据库受影响的行数。
     */
    fun save(@Param("enItemName") enItemName: String, @Param("otherName") otherName: String): Int
    /** 读取别名表中的全部记录。 */
    fun findAll(): List<WfOtherNameEntity>
    /** 删除主键为 [id] 的别名记录。 */
    fun delete(@Param("id") id: Int)
    /** 将主键为 [id] 的别名记录更新为 [otherName]。 */
    fun update(@Param("id") id: Int, @Param("otherName") otherName: String)
}