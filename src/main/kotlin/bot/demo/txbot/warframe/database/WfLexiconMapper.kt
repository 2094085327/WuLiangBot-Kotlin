package bot.demo.txbot.warframe.database

import com.baomidou.mybatisplus.core.mapper.BaseMapper
import org.apache.ibatis.annotations.Insert
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import org.apache.ibatis.annotations.Select

@Mapper
interface WfLexiconMapper : BaseMapper<WfLexiconEntity?> {
    @Insert(
        """
        INSERT IGNORE INTO wfLexicon (id, en_item_name, zh_item_name, url_name)  
        VALUES (#{entity.id}, #{entity.enItemName}, #{entity.zhItemName}, #{entity.urlName});
        """
    )
    fun insertIgnore(@Param("entity") wfLexiconEntity: WfLexiconEntity)

    @Select(
        """
        SELECT LOWER(wf_other_name.en_item_name) 
        FROM wf_other_name 
        WHERE LOWER(wf_other_name.other_name) = LOWER(#{otherName})
        """
    )
    fun selectByZhItemName(@Param("otherName") otherName: String): String?

    @Select(
        """
        SELECT wf_other_name.other_name 
        FROM wf_other_name 
        WHERE LOWER(wf_other_name.en_item_name) = LOWER(#{enItemName})
        """
    )
    fun selectByEnItemName(@Param("enItemName") enItemName: String): List<String>?
}