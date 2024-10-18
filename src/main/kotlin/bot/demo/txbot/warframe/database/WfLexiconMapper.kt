package bot.demo.txbot.warframe.database

import com.baomidou.mybatisplus.core.mapper.BaseMapper
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param

@Mapper
interface WfLexiconMapper : BaseMapper<WfLexiconEntity?> {
    fun selectByZhItemName(@Param("otherName") otherName: String): String?

    fun selectByEnItemName(@Param("enItemName") enItemName: String): List<String>?

    fun insertOrUpdateBatch(wfLexiconEntityList: List<WfLexiconEntity>)

    fun insertNewOtherName(@Param("enItemName") enItemName: String, @Param("otherName") otherName: String)

    fun selectEnFromOther(@Param("key") key: String): List<String>

    fun selectAllOtherName(): List<WfOtherNameEntity>

    fun deleteOtherNameById(@Param("id") id: Int)

    fun updateOtherNameById(@Param("id") id: Int, @Param("otherName") zhName: String)
}