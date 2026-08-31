package bot.wuliang.mapper

import bot.wuliang.entity.WfRivenEntity
import com.baomidou.mybatisplus.core.mapper.BaseMapper
import org.apache.ibatis.annotations.Insert
import org.apache.ibatis.annotations.Mapper
import org.apache.ibatis.annotations.Param
import org.apache.ibatis.annotations.Select

@Mapper
interface WfRivenMapper : BaseMapper<WfRivenEntity?> {
    /**
     * 生成一条多 values 的 upsert，避免逐条调用 BaseMapper.insert 带来的数据库往返。
     * 调用方必须保证 [list] 非空，否则 foreach 无法生成合法的 VALUES 子句。
     */
    @Insert(
        """
        <script>
        INSERT INTO wf_riven
            (id, url_name, en, zh, r_group, req_mastery_rank, riven_type, disposition)
        VALUES
        <foreach collection="list" item="item" separator=",">
            (#{item.id}, #{item.urlName}, #{item.enName}, #{item.zhName}, #{item.rGroup},
             #{item.reqMasteryRank}, #{item.rivenType}, #{item.disposition})
        </foreach>
        ON DUPLICATE KEY UPDATE
            id = VALUES(id),
            en = VALUES(en),
            zh = VALUES(zh),
            url_name = VALUES(url_name),
            r_group = VALUES(r_group),
            req_mastery_rank = VALUES(req_mastery_rank),
            riven_type = VALUES(riven_type),
            disposition = VALUES(disposition)
        </script>
        """
    )
    fun insertOrUpdateBatch(@Param("list") list: List<WfRivenEntity>)

    /** 查询标准紫卡武器目录；玄骸和信条武器由各自目录维护。 */
    @Select(
        """
        SELECT
            id,
            url_name AS urlName,
            en AS enName,
            zh AS zhName,
            r_group AS rGroup
        FROM wf_riven
        WHERE r_group NOT IN ('lich', 'sister')
        ORDER BY url_name
        """
    )
    fun selectAllRiven(): List<WfRivenEntity>
}