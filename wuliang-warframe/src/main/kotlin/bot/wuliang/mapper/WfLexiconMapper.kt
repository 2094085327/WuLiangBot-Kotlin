package bot.wuliang.mapper

import bot.wuliang.entity.WfLexiconEntity
import bot.wuliang.entity.WfOtherNameEntity
import com.baomidou.mybatisplus.core.mapper.BaseMapper
import org.apache.ibatis.annotations.Delete
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param

@Mapper
interface WfLexiconMapper : BaseMapper<WfLexiconEntity?> {
    fun selectByZhItemName(@Param("otherName") otherName: String): String?

    fun selectByEnItemName(@Param("enItemName") enItemName: String): List<String>?

    fun selectZhNamesList(keys:List<String>): List<Map<String, String>>

    fun insertOrUpdateBatch(wfLexiconEntityList: List<WfLexiconEntity>)

    fun insertNewOtherName(@Param("enItemName") enItemName: String, @Param("otherName") otherName: String): Int

    fun selectAllOtherName(): List<WfOtherNameEntity>

    @Delete("delete from wf_other_name where id = #{id}")
    fun deleteOtherNameById(@Param("id") id: Int)

    fun updateOtherNameById(@Param("id") id: Int, @Param("otherName") zhName: String)
}