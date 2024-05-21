package bot.demo.txbot.warframe.database

import com.baomidou.mybatisplus.core.mapper.BaseMapper
import org.apache.ibatis.annotations.Insert
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param

@Mapper
interface WfRivenMapper : BaseMapper<WfRivenEntity?> {
    @Insert("INSERT IGNORE INTO wfRiven (id, url_name, en , zh, `group`)  VALUES (#{entity.id}, #{entity.urlName}, #{entity.enName}, #{entity.zhName}, #{entity.group});")
    fun insertIgnore(@Param("entity") wfRivenEntity: WfRivenEntity)
}